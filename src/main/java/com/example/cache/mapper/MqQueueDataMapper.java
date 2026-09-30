package com.example.cache.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.cache.domain.entity.MqQueueData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MqQueueDataMapper extends BaseMapper<MqQueueData> {

    MqQueueData selectHead(@Param("queueName") String queueName);

    List<String> selectRecoverableQueueNames(@Param("now") long now);
}
