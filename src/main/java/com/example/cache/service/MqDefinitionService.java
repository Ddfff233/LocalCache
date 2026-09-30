package com.example.cache.service;

import com.example.cache.domain.entity.MqExchange;
import com.example.cache.domain.entity.MqQueue;
import com.example.cache.domain.enums.CacheMqExchangeType;

import java.util.List;

public interface MqDefinitionService {

    MqExchange declareExchange(String exchangeName, CacheMqExchangeType type);

    MqQueue declareQueue(String queueName);

    void bind(String queueName, String exchangeName, String routingKey);

    List<MqQueue> route(String exchangeName, String routingKey);

    MqExchange getExchange(String exchangeName);

    void initializeDefaultDeadLetterQueue();
}
