package com.example.cache.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.cache.config.CacheMqProperties;
import com.example.cache.domain.entity.MqExchange;
import com.example.cache.domain.entity.MqQueue;
import com.example.cache.domain.entity.MqQueueData;
import com.example.cache.domain.entity.MqQueueDeadLetter;
import com.example.cache.domain.enums.CacheMqMessageStatus;
import com.example.cache.domain.mq.CacheMqFailureResult;
import com.example.cache.exception.custom.BusinessException;
import com.example.cache.mapper.MqQueueDataMapper;
import com.example.cache.mapper.MqQueueDeadLetterMapper;
import com.example.cache.service.MqMessageService;
import com.example.cache.utils.ExponentialBackoffUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MqMessageServiceImpl implements MqMessageService {

    private static final int MQ_ERROR_CODE = 51000;

    private final MqQueueDataMapper queueDataMapper;
    private final MqQueueDeadLetterMapper deadLetterMapper;
    private final CacheMqProperties properties;

    public MqMessageServiceImpl(MqQueueDataMapper queueDataMapper,
                                MqQueueDeadLetterMapper deadLetterMapper,
                                CacheMqProperties properties) {
        this.queueDataMapper = queueDataMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MqQueueData> createQueueData(String messageId, MqExchange exchange,
                                             Collection<MqQueue> queues, String routingKey,
                                             byte[] payload, String payloadType,
                                             String headersJson) {
        try {
            List<MqQueueData> created = new ArrayList<>();
            for (MqQueue queue : queues) {
                MqQueueData data = new MqQueueData();
                data.setMessageId(messageId);
                data.setQueueId(queue.getId());
                data.setQueueName(queue.getQueueName());
                data.setExchangeId(exchange.getId());
                data.setExchangeName(exchange.getExchangeName());
                data.setRoutingKey(routingKey == null ? "" : routingKey);
                data.setPayload(payload);
                data.setPayloadType(payloadType);
                data.setHeaders(headersJson);
                data.setStatus(CacheMqMessageStatus.PENDING);
                data.setRetryCount(0);
                data.setDelFlag(0);
                if (queueDataMapper.insert(data) != 1) {
                    throw new IllegalStateException("保存 Queue 消息失败");
                }
                created.add(data);
            }
            if (created.size() != queues.size()) {
                throw new IllegalStateException("Queue 消息保存数量不一致");
            }
            return created;
        } catch (Exception exception) {
            log.error("持久化 MQ 消息失败，消息标识：{}", messageId, exception);
            throw new BusinessException(MQ_ERROR_CODE, "持久化 MQ 消息失败", exception);
        }
    }

    @Override
    public MqQueueData findHead(String queueName) {
        return queueDataMapper.selectHead(queueName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markProcessing(long id, long ackDeadlineAt, String deliveryToken) {
        return queueDataMapper.update(null, new LambdaUpdateWrapper<MqQueueData>()
                .eq(MqQueueData::getId, id)
                .in(MqQueueData::getStatus, CacheMqMessageStatus.PENDING,
                        CacheMqMessageStatus.WAITING_RETRY)
                .set(MqQueueData::getStatus, CacheMqMessageStatus.PROCESSING)
                .set(MqQueueData::getAckDeadlineAt, ackDeadlineAt)
                .set(MqQueueData::getDeliveryToken, deliveryToken)
                .set(MqQueueData::getNextRetryAt, null)) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acknowledge(long id, String deliveryToken) {
        return queueDataMapper.update(null, new LambdaUpdateWrapper<MqQueueData>()
                .eq(MqQueueData::getId, id)
                .eq(MqQueueData::getStatus, CacheMqMessageStatus.PROCESSING)
                .eq(MqQueueData::getDeliveryToken, deliveryToken)
                .set(MqQueueData::getStatus, CacheMqMessageStatus.ACKED)
                .set(MqQueueData::getAckDeadlineAt, null)
                .set(MqQueueData::getDeliveryToken, null)
                .set(MqQueueData::getLastError, null)) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CacheMqFailureResult handleFailure(long queueDataId, String deliveryToken,
                                              String failureReason, long now) {
        MqQueueData data = queueDataMapper.selectById(queueDataId);
        if (data == null || data.getStatus() != CacheMqMessageStatus.PROCESSING
                || deliveryToken == null || !deliveryToken.equals(data.getDeliveryToken())) {
            return CacheMqFailureResult.unchanged();
        }
        String normalizedReason = normalizeFailureReason(failureReason);
        int retryCount = data.getRetryCount() == null ? 0 : data.getRetryCount();
        if (retryCount < properties.getRetry().getMaxAttempts()) {
            int nextRetryCount = retryCount + 1;
            Duration delay = ExponentialBackoffUtils.calculateDelay(nextRetryCount,
                    properties.getRetry().getInitialDelay(),
                    properties.getRetry().getMultiplier(),
                    properties.getRetry().getMaxDelay());
            long nextRetryAt = now + delay.toMillis();
            int updated = queueDataMapper.update(null, new LambdaUpdateWrapper<MqQueueData>()
                    .eq(MqQueueData::getId, queueDataId)
                    .eq(MqQueueData::getStatus, CacheMqMessageStatus.PROCESSING)
                    .eq(MqQueueData::getDeliveryToken, deliveryToken)
                    .set(MqQueueData::getStatus, CacheMqMessageStatus.WAITING_RETRY)
                    .set(MqQueueData::getRetryCount, nextRetryCount)
                    .set(MqQueueData::getNextRetryAt, nextRetryAt)
                    .set(MqQueueData::getAckDeadlineAt, null)
                    .set(MqQueueData::getDeliveryToken, null)
                    .set(MqQueueData::getLastError, normalizedReason));
            return updated == 1
                    ? new CacheMqFailureResult(true, true, false, nextRetryAt)
                    : CacheMqFailureResult.unchanged();
        }
        return moveToDeadLetter(data, deliveryToken, normalizedReason, now);
    }

    @Override
    public Set<String> findRecoverableQueueNames(long now) {
        return new LinkedHashSet<>(queueDataMapper.selectRecoverableQueueNames(now));
    }

    private CacheMqFailureResult moveToDeadLetter(MqQueueData data, String deliveryToken,
                                                   String failureReason, long now) {
        MqQueueDeadLetter deadLetter = new MqQueueDeadLetter();
        deadLetter.setSourceQueueDataId(data.getId());
        deadLetter.setMessageId(data.getMessageId());
        deadLetter.setSourceQueueId(data.getQueueId());
        deadLetter.setSourceQueueName(data.getQueueName());
        deadLetter.setExchangeId(data.getExchangeId());
        deadLetter.setExchangeName(data.getExchangeName());
        deadLetter.setRoutingKey(data.getRoutingKey());
        deadLetter.setPayload(data.getPayload());
        deadLetter.setPayloadType(data.getPayloadType());
        deadLetter.setHeaders(data.getHeaders());
        deadLetter.setFailureReason(failureReason);
        deadLetter.setRetryCount(data.getRetryCount());
        deadLetter.setDeadLetterAt(now);
        deadLetter.setDelFlag(0);
        try {
            if (deadLetterMapper.insert(deadLetter) != 1) {
                throw new IllegalStateException("保存死信消息失败");
            }
            int updated = queueDataMapper.update(null,
                    new LambdaUpdateWrapper<MqQueueData>()
                            .eq(MqQueueData::getId, data.getId())
                            .eq(MqQueueData::getStatus, CacheMqMessageStatus.PROCESSING)
                            .eq(MqQueueData::getDeliveryToken, deliveryToken)
                            .set(MqQueueData::getStatus, CacheMqMessageStatus.DEAD_LETTER)
                            .set(MqQueueData::getAckDeadlineAt, null)
                            .set(MqQueueData::getDeliveryToken, null)
                            .set(MqQueueData::getNextRetryAt, null)
                            .set(MqQueueData::getLastError, failureReason));
            if (updated != 1) {
                throw new IllegalStateException("更新普通消息死信状态失败");
            }
            return new CacheMqFailureResult(true, false, true, null);
        } catch (Exception exception) {
            log.error("转移死信失败，消息标识：{}，Queue：{}",
                    data.getMessageId(), data.getQueueName(), exception);
            throw new BusinessException(MQ_ERROR_CODE, "转移死信失败", exception);
        }
    }

    private String normalizeFailureReason(String failureReason) {
        String reason = failureReason == null || failureReason.isBlank()
                ? "消费消息失败" : failureReason;
        return reason.length() <= 2000 ? reason : reason.substring(0, 2000);
    }
}
