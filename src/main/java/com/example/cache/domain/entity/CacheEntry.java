package com.example.cache.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 缓存条目持久化实体。
 */
@TableName(value ="cache_entry")
@Data
public class CacheEntry {
    @TableId(value = "key")
    private String key;

    @TableField(value = "expire_at")
    private Long expireAt;

    @TableField(value = "create_at")
    private Long createAt;

    @TableField(value = "update_at")
    private Long updateAt;

    @TableField(value = "value")
    private byte[] value;
}
