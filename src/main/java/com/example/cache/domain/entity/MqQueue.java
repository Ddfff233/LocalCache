package com.example.cache.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("MQ_QUEUE")
public class MqQueue {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String queueName;
    private Integer deadLetter;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Long createAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateAt;
    @TableLogic
    private Integer delFlag;
}
