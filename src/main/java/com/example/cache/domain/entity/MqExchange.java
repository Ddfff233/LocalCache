package com.example.cache.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.cache.domain.enums.CacheMqExchangeType;
import lombok.Data;

@Data
@TableName("MQ_EXCHANGE")
public class MqExchange {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String exchangeName;
    private CacheMqExchangeType exchangeType;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Long createAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateAt;
    @TableLogic
    private Integer delFlag;
}
