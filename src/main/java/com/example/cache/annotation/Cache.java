package com.example.cache.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cache {

    /**
     * 缓存键表达式，为空时根据方法和参数自动生成。
     *
     * @return 缓存键表达式
     */
    String key() default "";

    /**
     * 缓存有效期秒数，零表示永久缓存。
     *
     * @return 缓存有效期秒数
     */
    long ttlSeconds() default 300L;
}
