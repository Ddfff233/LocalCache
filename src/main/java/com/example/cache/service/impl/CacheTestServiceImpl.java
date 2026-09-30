package com.example.cache.service.impl;

import com.example.cache.annotation.Cache;
import com.example.cache.domain.vo.CacheTestVO;
import com.example.cache.exception.custom.BusinessException;
import com.example.cache.service.CacheTestService;
import com.example.cache.utils.CacheUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 生成用于验证方法缓存的测试数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheTestServiceImpl implements CacheTestService {

    private static final int CACHE_TEST_ERROR_CODE = 50000;

    private final CacheUtils cacheUtils;

    @Override
    @Cache(key = "'cache:test:' + #id", ttlSeconds = 60)
    public CacheTestVO getCachedResult(Long id) {
        long generatedAt = System.currentTimeMillis();
        log.info("生成缓存测试数据，标识：{}，生成时间：{}", id, generatedAt);
        return new CacheTestVO(id, generatedAt);
    }

    @Override
    public void setCache(String key, String value) {
        validateKey(key);
        if (value == null) {
            throw new BusinessException(CACHE_TEST_ERROR_CODE, "缓存值不能为空");
        }
        try {
            cacheUtils.set(key, value.getBytes(StandardCharsets.UTF_8), TimeUnit.SECONDS,10);
            log.info("写入缓存测试数据成功，缓存键摘要：{}", keyDigest(key));
        } catch (RuntimeException exception) {
            log.error("写入缓存测试数据失败，缓存键摘要：{}", keyDigest(key), exception);
            throw new BusinessException(CACHE_TEST_ERROR_CODE, "写入缓存失败", exception);
        }
    }

    @Override
    public String getCache(String key) {
        validateKey(key);
        byte[] value;
        try {
            value = cacheUtils.get(key);
        } catch (RuntimeException exception) {
            log.error("读取缓存测试数据失败，缓存键摘要：{}", keyDigest(key), exception);
            throw new BusinessException(CACHE_TEST_ERROR_CODE, "读取缓存失败", exception);
        }
        if (value == null) {
            log.warn("缓存测试数据不存在，缓存键摘要：{}", keyDigest(key));
            throw new BusinessException(CACHE_TEST_ERROR_CODE, "缓存数据不存在");
        }
        log.info("读取缓存测试数据成功，缓存键摘要：{}", keyDigest(key));
        return new String(value, StandardCharsets.UTF_8);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(CACHE_TEST_ERROR_CODE, "缓存键不能为空");
        }
    }

    private String keyDigest(String key) {
        return Integer.toHexString(key.hashCode());
    }
}
