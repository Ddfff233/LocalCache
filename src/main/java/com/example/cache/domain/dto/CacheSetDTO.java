package com.example.cache.domain.dto;

import lombok.Data;

/**
 * 写入缓存的请求参数。
 */
@Data
public class CacheSetDTO {

    private String key;
    private String value;
}
