package com.example.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "cache.mq")
public class CacheMqProperties {

    private Duration ackTimeout = Duration.ofSeconds(30);
    private Duration recoveryInterval = Duration.ofSeconds(1);
    private String deadLetterQueue = "MQ_DEFAULT_DEAD_LETTER";
    private Consumer consumer = new Consumer();
    private Retry retry = new Retry();

    @Data
    public static class Consumer {
        private int corePoolSize = 4;
        private int maxPoolSize = 16;
        private int queueCapacity = 1000;
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofSeconds(1);
        private double multiplier = 2D;
        private Duration maxDelay = Duration.ofSeconds(30);
    }
}
