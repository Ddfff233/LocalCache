package com.example.cache.mapper;

import com.example.cache.domain.entity.CacheEntry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 缓存条目数据访问接口。
 */
@Mapper
public interface CacheEntryMapper extends BaseMapper<CacheEntry> {

}




