package com.example.cache.utils;

import com.example.cache.domain.enums.CacheMqExchangeType;
import com.example.cache.domain.mq.CacheMqSendResult;
import com.example.cache.manager.mq.CacheMqProducer;
import com.example.cache.service.MqDefinitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class CacheMqUtils {

    private final MqDefinitionService definitionService;
    private final CacheMqProducer producer;

    public CacheMqUtils(MqDefinitionService definitionService, CacheMqProducer producer) {
        this.definitionService = definitionService;
        this.producer = producer;
    }

    public void declareExchange(String name, CacheMqExchangeType type) {
        definitionService.declareExchange(name, type);
    }

    public void declareQueue(String name) {
        definitionService.declareQueue(name);
    }

    public void bind(String queueName, String exchangeName, String routingKey) {
        definitionService.bind(queueName, exchangeName, routingKey);
    }

    public <T> CacheMqSendResult send(String exchangeName, String routingKey, T payload) {
        return send(exchangeName, routingKey, payload, Map.of());
    }

    public <T> CacheMqSendResult send(String exchangeName, String routingKey,
                                      T payload, Map<String, String> headers) {
        return producer.send(exchangeName, routingKey, payload, headers);
    }
}
