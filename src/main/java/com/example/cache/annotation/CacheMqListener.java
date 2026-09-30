package com.example.cache.annotation;

import com.example.cache.domain.enums.CacheMqAckMode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheMqListener {

    String queue();

    CacheMqAckMode ackMode() default CacheMqAckMode.AUTO;
}
