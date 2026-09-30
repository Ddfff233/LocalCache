package com.example.cache.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("MQ_QUEUE_EXCHANGE")
public class MqQueueExchange {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long queueId;
    private Long exchangeId;
    private String routingKey;
    @TableField(fill = FieldFill.INSERT)
    private Long createAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateAt;
    @TableLogic
    private Integer delFlag;
}
