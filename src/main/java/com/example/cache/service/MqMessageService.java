package com.example.cache.service;

import com.example.cache.domain.entity.MqExchange;
import com.example.cache.domain.entity.MqQueue;
import com.example.cache.domain.entity.MqQueueData;
import com.example.cache.domain.mq.CacheMqFailureResult;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MqMessageService {

    List<MqQueueData> createQueueData(String messageId, MqExchange exchange,
                                      Collection<MqQueue> queues, String routingKey,
                                      byte[] payload, String payloadType, String headersJson);

    MqQueueData findHead(String queueName);

    boolean markProcessing(long id, long ackDeadlineAt);

    boolean acknowledge(long id);

    CacheMqFailureResult handleFailure(long queueDataId, String failureReason, long now);

    Set<String> findRecoverableQueueNames(long now);
}
