package com.example.cache.domain.enums;

public enum CacheMqMessageStatus {
    PENDING,
    PROCESSING,
    WAITING_RETRY,
    ACKED,
    DEAD_LETTER
}
