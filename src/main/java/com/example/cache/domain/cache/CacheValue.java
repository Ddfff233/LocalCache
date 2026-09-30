package com.example.cache.domain.cache;

import java.util.Arrays;

/**
 * 包含可选绝对过期时间的不可变缓存值。
 */
public final class CacheValue {

    private final byte[] value;
    private final Long expireAt;
    private final long createAt;
    private final long updateAt;

    public CacheValue(byte[] value, Long expireAt, long createAt, long updateAt) {
        this.value = Arrays.copyOf(value, value.length);
        this.expireAt = expireAt;
        this.createAt = createAt;
        this.updateAt = updateAt;
    }

    public byte[] getValue() {
        return Arrays.copyOf(value, value.length);
    }

    public Long getExpireAt() {
        return expireAt;
    }

    public long getCreateAt() {
        return createAt;
    }

    public long getUpdateAt() {
        return updateAt;
    }
}
