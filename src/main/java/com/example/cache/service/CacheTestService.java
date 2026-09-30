package com.example.cache.service;

import com.example.cache.domain.vo.CacheTestVO;

/**
 * 提供缓存功能验证所需的测试数据。
 */
public interface CacheTestService {

    /**
     * 获取指定标识对应的缓存测试结果。
     *
     * @param id 测试标识
     * @return 缓存测试结果
     */
    CacheTestVO getCachedResult(Long id);

    /**
     * 永久写入字符串缓存。
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    void setCache(String key, String value);

    /**
     * 读取字符串缓存。
     *
     * @param key 缓存键
     * @return 缓存值
     */
    String getCache(String key);
}
