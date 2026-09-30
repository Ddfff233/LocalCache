package com.example.cache.domain.event;

import java.util.Set;

public record CacheMqQueueWakeEvent(Set<String> queueNames) {

    public CacheMqQueueWakeEvent {
        queueNames = Set.copyOf(queueNames);
    }
}
