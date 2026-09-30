package com.example.cache.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.cache.domain.entity.CacheEntry;

import java.util.Collection;

/**
 * 缓存条目持久化服务。
 */

public interface CacheEntryService extends IService<CacheEntry> {

    /**
     * 在同一事务中应用待处理的缓存变更。
     *
     * @param upserts 待新增或更新的缓存条目
     * @param deletes 待删除的缓存键
     */
    void applyChanges(Collection<CacheEntry> upserts, Collection<String> deletes);
}
