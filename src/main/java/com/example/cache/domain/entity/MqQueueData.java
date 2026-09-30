package com.example.cache.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.cache.domain.enums.CacheMqMessageStatus;
import lombok.Data;
import org.apache.ibatis.type.ByteArrayTypeHandler;

@Data
@TableName(value = "MQ_QUEUE_DATA", autoResultMap = true)
public class MqQueueData {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private Long queueId;
    private String queueName;
    private Long exchangeId;
    private String exchangeName;
    private String routingKey;
    @TableField(typeHandler = ByteArrayTypeHandler.class)
    private byte[] payload;
    private String payloadType;
    private String headers;
    private CacheMqMessageStatus status;
    private Integer retryCount;
    private Long nextRetryAt;
    private Long ackDeadlineAt;
    private String lastError;
    @TableField(fill = FieldFill.INSERT)
    private Long createAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateAt;
    @TableLogic
    private Integer delFlag;
}
