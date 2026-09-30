package com.example.cache.config;

import com.example.cache.domain.entity.MqExchange;
import com.example.cache.domain.entity.MqQueue;
import com.example.cache.domain.entity.MqQueueExchange;
import com.example.cache.utils.CacheMqRoutingMatcher;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caffeine实现MQ队列事件订阅的配置
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CacheMqProperties.class)
public class CacheMqConfig {

    @Bean("cacheMqExecutor")
    public ThreadPoolTaskExecutor cacheMqExecutor(CacheMqProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getConsumer().getCorePoolSize());
        executor.setMaxPoolSize(properties.getConsumer().getMaxPoolSize());
        executor.setQueueCapacity(properties.getConsumer().getQueueCapacity());
        executor.setThreadNamePrefix("本地MQ消费-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        log.info("本地 MQ 异步消费线程池初始化完成");
        return executor;
    }

    @Bean("mqExchangeCache")
    public Cache<String, MqExchange> mqExchangeCache() {
        return Caffeine.newBuilder().maximumSize(10_000).build();
    }

    @Bean("mqQueueCache")
    public Cache<String, MqQueue> mqQueueCache() {
        return Caffeine.newBuilder().maximumSize(10_000).build();
    }

    @Bean("mqBindingCache")
    public Cache<Long, List<MqQueueExchange>> mqBindingCache() {
        return Caffeine.newBuilder().maximumSize(10_000).build();
    }

    @Bean("mqQueueRunningCache")
    public Cache<String, AtomicBoolean> mqQueueRunningCache() {
        return Caffeine.newBuilder().maximumSize(100_000).build();
    }

    @Bean
    public CacheMqRoutingMatcher cacheMqRoutingMatcher() {
        return new CacheMqRoutingMatcher();
    }
}
