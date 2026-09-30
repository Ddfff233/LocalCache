package com.example.cache.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.cache.domain.entity.CacheEntry;
import com.example.cache.exception.custom.BusinessException;
import com.example.cache.mapper.CacheEntryMapper;
import com.example.cache.service.CacheEntryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * 缓存条目持久化服务实现。
 */
@Service
@Slf4j
public class CacheEntryServiceImpl extends ServiceImpl<CacheEntryMapper, CacheEntry>
        implements CacheEntryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyChanges(Collection<CacheEntry> upserts, Collection<String> deletes) {
        try {
            if (!deletes.isEmpty()) {
                removeByIds(deletes);
            }
            if (!upserts.isEmpty() && !saveOrUpdateBatch(upserts)) {
                throw new IllegalStateException("批量保存缓存条目失败");
            }
        } catch (Exception exception) {
            log.error("批量应用缓存持久化变更失败", exception);
            throw new BusinessException(50301, "缓存持久化失败", exception);
        }
    }
}
