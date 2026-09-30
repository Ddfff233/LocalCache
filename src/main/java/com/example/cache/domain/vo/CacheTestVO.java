package com.example.cache.domain.vo;

import com.example.cache.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存测试接口的响应数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheTestVO extends BaseEntity {

    private Long id;
    private Long generatedAt;
}
