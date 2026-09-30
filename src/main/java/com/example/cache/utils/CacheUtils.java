package com.example.cache.utils;

import com.example.cache.config.CacheConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

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
