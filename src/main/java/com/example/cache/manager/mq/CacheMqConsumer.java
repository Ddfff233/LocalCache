package com.example.cache.manager.mq;

import com.example.cache.annotation.CacheMqListener;
import com.example.cache.config.CacheMqProperties;
import com.example.cache.domain.entity.MqQueueData;
import com.example.cache.domain.enums.CacheMqAckMode;
import com.example.cache.domain.enums.CacheMqMessageStatus;
import com.example.cache.domain.event.CacheMqQueueWakeEvent;
import com.example.cache.domain.mq.CacheMqAckContext;
import com.example.cache.domain.mq.CacheMqFailureResult;
import com.example.cache.exception.custom.BusinessException;
import com.example.cache.service.MqDefinitionService;
import com.example.cache.service.MqMessageService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 扫描本地 MQ 监听方法，并保证单 Queue 串行异步消费。
 */
@Slf4j
@Component
public class CacheMqConsumer implements SmartInitializingSingleton {

    private static final int MQ_ERROR_CODE = 51000;

    private final ApplicationContext applicationContext;
    private final MqDefinitionService definitionService;
    private final MqMessageService messageService;
    private final CacheMqProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Cache<String, AtomicBoolean> queueRunningCache;
    private final Map<String, ListenerDefinition> listeners = new ConcurrentHashMap<>();

    public CacheMqConsumer(
            ApplicationContext applicationContext,
            MqDefinitionService definitionService,
            MqMessageService messageService,
            CacheMqProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("cacheMqExecutor") Executor executor,
            @Qualifier("mqQueueRunningCache") Cache<String, AtomicBoolean> queueRunningCache) {
        this.applicationContext = applicationContext;
        this.definitionService = definitionService;
        this.messageService = messageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.queueRunningCache = queueRunningCache;
    }

    @Override
    public void afterSingletonsInstantiated() {
        registerListeners();
        definitionService.initializeDefaultDeadLetterQueue();
        recoverQueues();
    }

    @EventListener
    public void onQueueWake(CacheMqQueueWakeEvent event) {
        event.queueNames().forEach(this::scheduleQueue);
    }

    @Scheduled(fixedDelayString = "${cache.mq.recovery-interval:1s}")
    public void recoverQueues() {
        messageService.findRecoverableQueueNames(System.currentTimeMillis())
                .forEach(this::scheduleQueue);
    }

    public void scheduleQueue(String queueName) {
        ListenerDefinition listener = listeners.get(queueName);
        if (listener == null) {
            return;
        }
        AtomicBoolean running = queueRunningCache.get(queueName, key -> new AtomicBoolean());
        if (running == null || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> consumeQueue(queueName, running));
        } catch (RuntimeException exception) {
            running.set(false);
            log.error("提交 MQ 异步消费任务失败，Queue：{}", queueName, exception);
        }
    }

    private void registerListeners() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException exception) {
                log.debug("跳过无法提前获取的 Bean，名称：{}", beanName, exception);
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            Map<Method, CacheMqListener> methods = MethodIntrospector.selectMethods(
                    targetClass,
                    (MethodIntrospector.MetadataLookup<CacheMqListener>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method,
                                    CacheMqListener.class));
            methods.forEach((method, annotation) -> registerListener(bean, method, annotation));
        }
    }

    private void registerListener(Object bean, Method method, CacheMqListener annotation) {
        validateListener(method, annotation);
        Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
        ReflectionUtils.makeAccessible(invocableMethod);
        JavaType payloadType = objectMapper.getTypeFactory()
                .constructType(method.getGenericParameterTypes()[0]);
        ListenerDefinition definition = new ListenerDefinition(
                bean, invocableMethod, annotation.ackMode(), payloadType);
        ListenerDefinition existing = listeners.putIfAbsent(annotation.queue(), definition);
        if (existing != null) {
            throw new BusinessException(MQ_ERROR_CODE,
                    "同一个 Queue 只能声明一个 MQ 监听方法：" + annotation.queue());
        }
    }

    private void validateListener(Method method, CacheMqListener annotation) {
        if (annotation.queue().isBlank()) {
            throw new BusinessException(MQ_ERROR_CODE, "MQ 监听 Queue 名称不能为空");
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new BusinessException(MQ_ERROR_CODE, "MQ 监听方法必须是 public 方法");
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (annotation.ackMode() == CacheMqAckMode.AUTO && parameterTypes.length != 1) {
            throw new BusinessException(MQ_ERROR_CODE,
                    "AUTO 模式 MQ 监听方法必须只有一个消息参数");
        }
        if (annotation.ackMode() == CacheMqAckMode.MANUAL
                && (parameterTypes.length != 2
                || parameterTypes[1] != CacheMqAckContext.class)) {
            throw new BusinessException(MQ_ERROR_CODE,
                    "MANUAL 模式 MQ 监听方法第二个参数必须是 CacheMqAckContext");
        }
    }

    private void consumeQueue(String queueName, AtomicBoolean running) {
        try {
            while (consumeHead(queueName)) {
                // 当前队首已经结束，继续处理下一条消息。
            }
        } finally {
            running.set(false);
            MqQueueData head = messageService.findHead(queueName);
            if (isImmediatelyExecutable(head, System.currentTimeMillis())) {
                scheduleQueue(queueName);
            }
        }
    }

    private boolean consumeHead(String queueName) {
        MqQueueData data = messageService.findHead(queueName);
        if (data == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (data.getStatus() == CacheMqMessageStatus.PROCESSING) {
            if (data.getAckDeadlineAt() != null && data.getAckDeadlineAt() <= now) {
                CacheMqFailureResult result = messageService.handleFailure(
                        data.getId(), data.getDeliveryToken(), "消息确认超时", now);
                return result.deadLettered();
            }
            return false;
        }
        if (data.getStatus() == CacheMqMessageStatus.WAITING_RETRY
                && (data.getNextRetryAt() == null || data.getNextRetryAt() > now)) {
            return false;
        }
        return deliver(data, now);
    }

    private boolean deliver(MqQueueData data, long now) {
        ListenerDefinition listener = listeners.get(data.getQueueName());
        if (listener == null) {
            return false;
        }
        long ackDeadlineAt = now + properties.getAckTimeout().toMillis();
        String deliveryToken = UUID.randomUUID().toString();
        if (!messageService.markProcessing(data.getId(), ackDeadlineAt, deliveryToken)) {
            return true;
        }
        try {
            Object payload = objectMapper.readValue(data.getPayload(), listener.payloadType());
            if (listener.ackMode() == CacheMqAckMode.AUTO) {
                listener.method().invoke(listener.bean(), payload);
                messageService.acknowledge(data.getId(), deliveryToken);
                return true;
            }
            CacheMqAckContext context = createAckContext(data, deliveryToken);
            listener.method().invoke(listener.bean(), payload, context);
            return context.isCompleted() && messageService.findHead(data.getQueueName()) == null;
        } catch (InvocationTargetException exception) {
            Throwable target = exception.getTargetException();
            handleConsumerFailure(data, deliveryToken, target);
            return false;
        } catch (Exception exception) {
            handleConsumerFailure(data, deliveryToken, exception);
            return false;
        }
    }

    private CacheMqAckContext createAckContext(MqQueueData data, String deliveryToken) {
        return new CacheMqAckContext(data.getMessageId(), () -> {
            boolean acknowledged = messageService.acknowledge(data.getId(), deliveryToken);
            if (acknowledged) {
                scheduleQueue(data.getQueueName());
            }
            return acknowledged;
        }, reason -> {
            CacheMqFailureResult result = messageService.handleFailure(
                    data.getId(), deliveryToken, reason, System.currentTimeMillis());
            if (result.stateChanged()) {
                scheduleQueue(data.getQueueName());
            }
            return result.stateChanged();
        });
    }

    private void handleConsumerFailure(MqQueueData data, String deliveryToken,
                                       Throwable throwable) {
        log.error("消费 MQ 消息失败，消息标识：{}，Queue：{}",
                data.getMessageId(), data.getQueueName(), throwable);
        String reason = throwable.getMessage() == null
                ? "消费监听方法执行失败" : throwable.getMessage();
        messageService.handleFailure(data.getId(), deliveryToken,
                reason, System.currentTimeMillis());
    }

    private boolean isImmediatelyExecutable(MqQueueData data, long now) {
        if (data == null) {
            return false;
        }
        return data.getStatus() == CacheMqMessageStatus.PENDING
                || data.getStatus() == CacheMqMessageStatus.WAITING_RETRY
                && data.getNextRetryAt() != null && data.getNextRetryAt() <= now
                || data.getStatus() == CacheMqMessageStatus.PROCESSING
                && data.getAckDeadlineAt() != null && data.getAckDeadlineAt() <= now;
    }

    private record ListenerDefinition(Object bean, Method method,
                                      CacheMqAckMode ackMode, JavaType payloadType) {
    }
}
