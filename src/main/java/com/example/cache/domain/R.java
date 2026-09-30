package com.example.cache.domain;

import java.time.Instant;

public record R<T>(int code, String message, T data, Instant timestamp) {

    public static <T> R<T> success(T data) {
        return new R<>(0, "success", data, Instant.now());
    }

    public static <T> R<T> failure(int code, String message) {
        return new R<>(code, message, null, Instant.now());
    }
}
