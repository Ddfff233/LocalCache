package com.example.cache.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cache.config.CacheMqProperties;
import com.example.cache.domain.entity.MqExchange;
import com.example.cache.domain.entity.MqQueue;
import com.example.cache.domain.entity.MqQueueExchange;
import com.example.cache.domain.enums.CacheMqExchangeType;
import com.example.cache.exception.custom.BusinessException;
import com.example.cache.mapper.MqExchangeMapper;
import com.example.cache.mapper.MqQueueExchangeMapper;
import com.example.cache.mapper.MqQueueMapper;
import com.example.cache.service.MqDefinitionService;
import com.example.cache.utils.CacheMqRoutingMatcher;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MqDefinitionServiceImpl implements MqDefinitionService {

    private static final int MQ_ERROR_CODE = 51000;

    private final MqExchangeMapper exchangeMapper;
    private final MqQueueMapper queueMapper;
    private final MqQueueExchangeMapper bindingMapper;
    private final CacheMqRoutingMatcher routingMatcher;
    private final CacheMqProperties properties;
    private final Cache<String, MqExchange> exchangeCache;
    private final Cache<String, MqQueue> queueCache;
    private final Cache<Long, List<MqQueueExchange>> bindingCache;

    public MqDefinitionServiceImpl(
            MqExchangeMapper exchangeMapper,
            MqQueueMapper queueMapper,
            MqQueueExchangeMapper bindingMapper,
            CacheMqRoutingMatcher routingMatcher,
            CacheMqProperties properties,
            @Qualifier("mqExchangeCache") Cache<String, MqExchange> exchangeCache,
            @Qualifier("mqQueueCache") Cache<String, MqQueue> queueCache,
            @Qualifier("mqBindingCache") Cache<Long, List<MqQueueExchange>> bindingCache) {
        this.exchangeMapper = exchangeMapper;
        this.queueMapper = queueMapper;
        this.bindingMapper = bindingMapper;
        this.routingMatcher = routingMatcher;
        this.properties = properties;
        this.exchangeCache = exchangeCache;
        this.queueCache = queueCache;
        this.bindingCache = bindingCache;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MqExchange declareExchange(String exchangeName, CacheMqExchangeType type) {
        validateName(exchangeName, "Exchange 名称不能为空");
        if (type == null) {
            throw new BusinessException(MQ_ERROR_CODE, "Exchange 类型不能为空");
        }
        MqExchange existing = findExchange(exchangeName);
        if (existing != null) {
            if (existing.getExchangeType() != type) {
                throw new BusinessException(MQ_ERROR_CODE, "同名 Exchange 的类型不一致");
            }
            return existing;
        }
        try {
            MqExchange exchange = new MqExchange();
            exchange.setExchangeName(exchangeName);
            exchange.setExchangeType(type);
            exchange.setStatus(1);
            exchange.setDelFlag(0);
            if (exchangeMapper.insert(exchange) != 1) {
                throw new IllegalStateException("新增 Exchange 记录失败");
            }
            exchangeCache.put(exchangeName, exchange);
            return exchange;
        } catch (Exception exception) {
            throw persistenceFailure("声明 Exchange 失败，名称：" + exchangeName, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MqQueue declareQueue(String queueName) {
        validateName(queueName, "Queue 名称不能为空");
        MqQueue existing = findQueue(queueName);
        if (existing != null) {
            return existing;
        }
        try {
            MqQueue queue = new MqQueue();
            queue.setQueueName(queueName);
            queue.setDeadLetter(0);
            queue.setStatus(1);
            queue.setDelFlag(0);
            if (queueMapper.insert(queue) != 1) {
                throw new IllegalStateException("新增 Queue 记录失败");
            }
            queueCache.put(queueName, queue);
            return queue;
        } catch (Exception exception) {
            throw persistenceFailure("声明 Queue 失败，名称：" + queueName, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(String queueName, String exchangeName, String routingKey) {
        validateName(queueName, "Queue 名称不能为空");
        validateName(exchangeName, "Exchange 名称不能为空");
        MqQueue queue = requireQueue(queueName);
        MqExchange exchange = requireExchange(exchangeName);
        String normalizedKey = normalizeBindingKey(exchange.getExchangeType(), routingKey);
        try {
            Long count = bindingMapper.selectCount(new LambdaQueryWrapper<MqQueueExchange>()
                    .eq(MqQueueExchange::getQueueId, queue.getId())
                    .eq(MqQueueExchange::getExchangeId, exchange.getId())
                    .eq(MqQueueExchange::getRoutingKey, normalizedKey));
            if (count > 0) {
                return;
            }
            MqQueueExchange binding = new MqQueueExchange();
            binding.setQueueId(queue.getId());
            binding.setExchangeId(exchange.getId());
            binding.setRoutingKey(normalizedKey);
            binding.setDelFlag(0);
            if (bindingMapper.insert(binding) != 1) {
                throw new IllegalStateException("新增 Binding 记录失败");
            }
            bindingCache.invalidate(exchange.getId());
        } catch (Exception exception) {
            throw persistenceFailure("绑定 Queue 与 Exchange 失败", exception);
        }
    }

    @Override
    public List<MqQueue> route(String exchangeName, String routingKey) {
        validateName(exchangeName, "Exchange 名称不能为空");
        MqExchange exchange = requireExchange(exchangeName);
        String actualRoutingKey = routingKey == null ? "" : routingKey;
        if (exchange.getExchangeType() != CacheMqExchangeType.FANOUT
                && actualRoutingKey.isBlank()) {
            throw new BusinessException(MQ_ERROR_CODE, "Routing Key 不能为空");
        }
        List<MqQueueExchange> bindings = findBindings(exchange.getId());
        Map<Long, MqQueue> routedQueues = new LinkedHashMap<>();
        for (MqQueueExchange binding : bindings) {
            if (!routingMatcher.matches(exchange.getExchangeType(),
                    binding.getRoutingKey(), actualRoutingKey)) {
                continue;
            }
            MqQueue queue = queueMapper.selectById(binding.getQueueId());
            if (queue != null && Integer.valueOf(1).equals(queue.getStatus())) {
                queueCache.put(queue.getQueueName(), queue);
                routedQueues.put(queue.getId(), queue);
            }
        }
        return new ArrayList<>(routedQueues.values());
    }

    @Override
    public MqExchange getExchange(String exchangeName) {
        validateName(exchangeName, "Exchange 名称不能为空");
        return requireExchange(exchangeName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initializeDefaultDeadLetterQueue() {
        MqQueue queue = declareQueue(properties.getDeadLetterQueue());
        if (Integer.valueOf(1).equals(queue.getDeadLetter())) {
            return;
        }
        try {
            queue.setDeadLetter(1);
            if (queueMapper.updateById(queue) != 1) {
                throw new IllegalStateException("更新默认死信 Queue 失败");
            }
            queueCache.put(queue.getQueueName(), queue);
        } catch (Exception exception) {
            throw persistenceFailure("初始化默认死信 Queue 失败", exception);
        }
    }

    private MqExchange requireExchange(String exchangeName) {
        MqExchange exchange = findExchange(exchangeName);
        if (exchange == null || !Integer.valueOf(1).equals(exchange.getStatus())) {
            throw new BusinessException(MQ_ERROR_CODE, "Exchange 不存在或已停用");
        }
        return exchange;
    }

    private MqQueue requireQueue(String queueName) {
        MqQueue queue = findQueue(queueName);
        if (queue == null || !Integer.valueOf(1).equals(queue.getStatus())) {
            throw new BusinessException(MQ_ERROR_CODE, "Queue 不存在或已停用");
        }
        return queue;
    }

    private MqExchange findExchange(String exchangeName) {
        MqExchange cached = exchangeCache.getIfPresent(exchangeName);
        if (cached != null) {
            return cached;
        }
        MqExchange exchange = exchangeMapper.selectOne(new LambdaQueryWrapper<MqExchange>()
                .eq(MqExchange::getExchangeName, exchangeName));
        if (exchange != null) {
            exchangeCache.put(exchangeName, exchange);
        }
        return exchange;
    }

    private MqQueue findQueue(String queueName) {
        MqQueue cached = queueCache.getIfPresent(queueName);
        if (cached != null) {
            return cached;
        }
        MqQueue queue = queueMapper.selectOne(new LambdaQueryWrapper<MqQueue>()
                .eq(MqQueue::getQueueName, queueName));
        if (queue != null) {
            queueCache.put(queueName, queue);
        }
        return queue;
    }

    private List<MqQueueExchange> findBindings(Long exchangeId) {
        List<MqQueueExchange> cached = bindingCache.getIfPresent(exchangeId);
        if (cached != null) {
            return cached;
        }
        List<MqQueueExchange> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<MqQueueExchange>()
                        .eq(MqQueueExchange::getExchangeId, exchangeId));
        List<MqQueueExchange> immutableBindings = List.copyOf(bindings);
        bindingCache.put(exchangeId, immutableBindings);
        return immutableBindings;
    }

    private String normalizeBindingKey(CacheMqExchangeType type, String routingKey) {
        if (type == CacheMqExchangeType.FANOUT) {
            return "";
        }
        if (routingKey == null || routingKey.isBlank()) {
            throw new BusinessException(MQ_ERROR_CODE, "Binding Routing Key 不能为空");
        }
        return routingKey;
    }

    private void validateName(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(MQ_ERROR_CODE, message);
        }
    }

    private BusinessException persistenceFailure(String message, Exception exception) {
        log.error(message, exception);
        return new BusinessException(MQ_ERROR_CODE, message, exception);
    }
}
