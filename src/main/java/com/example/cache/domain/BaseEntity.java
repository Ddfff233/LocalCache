package com.example.cache.domain;

import lombok.Data;

import java.util.Map;

@Data
public class BaseEntity {

    private Map<String,String> params;

    public static BaseEntity build(Object o) {
        return BaseEntity.class.cast(o);
    }
}
