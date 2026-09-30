package com.example.cache.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.cache.domain.entity.CacheEntry;
import com.example.cache.mapper.CacheEntryMapper;
import com.example.cache.service.CacheEntryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * 缓存条目持久化服务实现。
 */
@Service
public class CacheEntryServiceImpl extends ServiceImpl<CacheEntryMapper, CacheEntry>
        implements CacheEntryService {

    @Override
    @Transactional
    public void applyChanges(Collection<CacheEntry> upserts, Collection<String> deletes) {
        if (!deletes.isEmpty()) {
            removeByIds(deletes);
        }
        if (!upserts.isEmpty()) {
            saveOrUpdateBatch(upserts);
        }
    }
}
