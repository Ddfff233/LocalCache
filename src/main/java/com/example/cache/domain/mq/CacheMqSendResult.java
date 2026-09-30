package com.example.cache.domain.mq;

public record CacheMqSendResult(String messageId, boolean confirmed,
                                int routedQueueCount, long confirmedAt) {
}
