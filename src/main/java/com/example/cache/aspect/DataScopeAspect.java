package com.example.cache.aspect;

import com.example.cache.annotation.DataScope;
import com.example.cache.domain.BaseEntity;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Aspect
@Component
public class DataScopeAspect {
    /**
     * 作为数据隔离切面，保证在查询数据库前对where进行拼接筛选
     */

    @Before("@annotation(dataScope)")
    public void doBefore(JoinPoint point, DataScope dataScope){
        Object entity = point.getArgs()[0];
        if(Objects.isNull(entity)){
            return;
        }
        BaseEntity entityBuild = BaseEntity.build(entity);

    }
}
