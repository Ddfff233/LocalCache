package com.example.cache.utils;

import com.example.cache.config.CacheConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 本地持久化缓存的统一操作入口。
 */
@Component
public class CacheUtils {

    private final CacheConfig cacheConfig;

    public CacheUtils(CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    public byte[] get(String key) {
        return cacheConfig.get(key);
    }

    public void set(String key, byte[] value) {
        cacheConfig.set(key, value, null);
    }

    public void set(String key, byte[] value, Duration ttl) {
        cacheConfig.set(key, value, ttl);
    }

    /**
     * 按指定时间单位设置缓存有效期。
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param unit 过期时间单位
     * @param timeout 过期时间值
     */
    public void set(String key, byte[] value, TimeUnit unit, long timeout) {
        if (unit == null) {
            throw new IllegalArgumentException("缓存过期时间单位不能为空");
        }
        set(key, value, Duration.of(timeout, unit.toChronoUnit()));
    }

    public boolean delete(String key) {
        return cacheConfig.delete(key);
    }

    public boolean hasKey(String key) {
        return cacheConfig.hasKey(key);
    }

    public boolean expire(String key, Duration ttl) {
        return cacheConfig.expire(key, ttl);
    }

    public Duration getExpire(String key) {
        return cacheConfig.getExpire(key);
    }
}
