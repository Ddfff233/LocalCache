package com.example.cache.domain.mq;

public record CacheMqFailureResult(boolean stateChanged, boolean retryScheduled,
                                   boolean deadLettered, Long nextRetryAt) {

    public static CacheMqFailureResult unchanged() {
        return new CacheMqFailureResult(false, false, false, null);
    }
}
