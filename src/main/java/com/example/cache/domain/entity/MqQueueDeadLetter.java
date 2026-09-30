package com.example.cache.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.ibatis.type.ByteArrayTypeHandler;

@Data
@TableName(value = "MQ_QUEUE_DEADLETTER", autoResultMap = true)
public class MqQueueDeadLetter {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceQueueDataId;
    private String messageId;
    private Long sourceQueueId;
    private String sourceQueueName;
    private Long exchangeId;
    private String exchangeName;
    private String routingKey;
    @TableField(typeHandler = ByteArrayTypeHandler.class)
    private byte[] payload;
    private String payloadType;
    private String headers;
    private String failureReason;
    private Integer retryCount;
    private Long deadLetterAt;
    @TableField(fill = FieldFill.INSERT)
    private Long createAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateAt;
    @TableLogic
    private Integer delFlag;
}
