package com.example.cache.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * 提供可复用的指数退避计算和同步重试能力。
 */
@Slf4j
public final class ExponentialBackoffUtils {

    private ExponentialBackoffUtils() {
    }

    public static Duration calculateDelay(int retryNumber, Duration initialDelay,
                                          double multiplier, Duration maxDelay) {
        validate(retryNumber, initialDelay, multiplier, maxDelay);
        double delayMillis = initialDelay.toMillis()
                * Math.pow(multiplier, retryNumber - 1D);
        long boundedMillis = delayMillis >= maxDelay.toMillis()
                ? maxDelay.toMillis() : (long) delayMillis;
        return Duration.ofMillis(Math.max(0L, boundedMillis));
    }

    public static <T> T execute(String operationName, Callable<T> task,
                                int maxAttempts, Duration initialDelay,
                                double multiplier, Duration maxDelay,
                                Function<Exception, ? extends RuntimeException> converter) {
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("重试操作名称不能为空");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("最大尝试次数必须大于零");
        }
        Objects.requireNonNull(task, "重试任务不能为空");
        Objects.requireNonNull(converter, "异常转换器不能为空");

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return task.call();
            } catch (Exception exception) {
                lastException = exception;
                log.warn("执行重试任务失败，操作：{}，当前次数：{}，最大次数：{}",
                        operationName, attempt, maxAttempts, exception);
                if (attempt < maxAttempts) {
                    sleep(calculateDelay(attempt, initialDelay, multiplier, maxDelay),
                            operationName, converter);
                }
            }
        }
        throw converter.apply(lastException);
    }

    private static void sleep(Duration delay, String operationName,
                              Function<Exception, ? extends RuntimeException> converter) {
        try {
            if (!delay.isZero()) {
                Thread.sleep(delay.toMillis());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("指数退避等待被中断，操作：{}", operationName, exception);
            throw converter.apply(exception);
        }
    }

    private static void validate(int retryNumber, Duration initialDelay,
                                 double multiplier, Duration maxDelay) {
        if (retryNumber <= 0) {
            throw new IllegalArgumentException("重试序号必须大于零");
        }
        Objects.requireNonNull(initialDelay, "初始退避时间不能为空");
        Objects.requireNonNull(maxDelay, "最大退避时间不能为空");
        if (initialDelay.isNegative() || maxDelay.isNegative()) {
            throw new IllegalArgumentException("退避时间不能为负数");
        }
        if (multiplier < 1D || Double.isNaN(multiplier)) {
            throw new IllegalArgumentException("退避倍率不能小于一");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("最大退避时间不能小于初始退避时间");
        }
    }
}
