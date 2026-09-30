package com.example.cache.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.cache.domain.entity.MqQueue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqQueueMapper extends BaseMapper<MqQueue> {
}
