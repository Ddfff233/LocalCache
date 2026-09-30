package com.example.cache.manager.mq;

import com.example.cache.domain.entity.MqExchange;
import com.example.cache.domain.entity.MqQueue;
import com.example.cache.domain.event.CacheMqQueueWakeEvent;
import com.example.cache.domain.mq.CacheMqSendResult;
import com.example.cache.exception.custom.BusinessException;
import com.example.cache.service.MqDefinitionService;
import com.example.cache.service.MqMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 负责消息序列化、持久化确认和消费唤醒。
 */
@Slf4j
@Component
public class CacheMqProducer {

    private static final int MQ_ERROR_CODE = 51000;

    private final MqDefinitionService definitionService;
    private final MqMessageService messageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public CacheMqProducer(MqDefinitionService definitionService,
                           MqMessageService messageService,
                           ApplicationEventPublisher eventPublisher,
                           ObjectMapper objectMapper) {
        this.definitionService = definitionService;
        this.messageService = messageService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public <T> CacheMqSendResult send(String exchangeName, String routingKey,
                                      T payload, Map<String, String> headers) {
        if (payload == null) {
            throw new BusinessException(MQ_ERROR_CODE, "MQ 消息体不能为空");
        }
        Map<String, String> safeHeaders = headers == null
                ? Map.of() : new LinkedHashMap<>(headers);
        byte[] serializedPayload;
        String serializedHeaders;
        try {
            serializedPayload = objectMapper.writeValueAsBytes(payload);
            serializedHeaders = objectMapper.writeValueAsString(safeHeaders);
        } catch (Exception exception) {
            log.error("序列化 MQ 消息失败，Exchange：{}", exchangeName, exception);
            throw new BusinessException(MQ_ERROR_CODE, "序列化 MQ 消息失败", exception);
        }

        MqExchange exchange = definitionService.getExchange(exchangeName);
        List<MqQueue> queues = definitionService.route(exchangeName, routingKey);
        if (queues.isEmpty()) {
            throw new BusinessException(MQ_ERROR_CODE, "消息没有匹配到任何 Queue");
        }

        String messageId = UUID.randomUUID().toString();
        messageService.createQueueData(messageId, exchange, queues, routingKey,
                serializedPayload, payload.getClass().getName(), serializedHeaders);
        Set<String> queueNames = queues.stream()
                .map(MqQueue::getQueueName)
                .collect(Collectors.toUnmodifiableSet());
        eventPublisher.publishEvent(new CacheMqQueueWakeEvent(queueNames));
        long confirmedAt = System.currentTimeMillis();
        log.debug("MQ 消息持久化确认成功，消息标识：{}，目标 Queue 数量：{}",
                messageId, queues.size());
        return new CacheMqSendResult(messageId, true, queues.size(), confirmedAt);
    }
}
