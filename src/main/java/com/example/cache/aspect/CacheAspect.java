package com.example.cache.aspect;

import com.example.cache.annotation.Cache;
import com.example.cache.exception.custom.BusinessException;
import com.example.cache.utils.CacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 统一处理方法级缓存的键生成、JSON 转换和有效期控制。
 */
@Slf4j
@Aspect
@Component
public class CacheAspect {

    private static final int CACHE_ERROR_CODE = 50000;
    private static final String KEY_PREFIX = "method-cache:";

    private final CacheUtils cacheUtils;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    public CacheAspect(CacheUtils cacheUtils, ObjectMapper objectMapper) {
        this.cacheUtils = cacheUtils;
        this.objectMapper = objectMapper;
    }

    /**
     * 优先返回缓存数据，未命中时执行目标方法并缓存非空结果。
     * 目标方法异常保持原样传播，缓存配置或 JSON 处理异常转换为业务异常。
     *
     * @param joinPoint 目标方法连接点
     * @param cacheAnnotation 缓存注解配置
     * @return 缓存值或目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(cacheAnnotation)")
    public Object cache(ProceedingJoinPoint joinPoint, Cache cacheAnnotation) throws Throwable {
        Method method = resolveMethod(joinPoint);
        validate(method, cacheAnnotation);
        String key = resolveKey(joinPoint, method, cacheAnnotation.key());
        return invokeWithCache(joinPoint, method, cacheAnnotation, key);
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return AopUtils.getMostSpecificMethod(signature.getMethod(),
                joinPoint.getTarget().getClass());
    }

    private void validate(Method method, Cache cacheAnnotation) {
        if (method.getReturnType() == Void.TYPE) {
            throw new BusinessException(CACHE_ERROR_CODE, "缓存方法必须声明返回值");
        }
        if (cacheAnnotation.ttlSeconds() < 0) {
            throw new BusinessException(CACHE_ERROR_CODE, "缓存有效期不能小于零");
        }
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, Method method, String expression) {
        if (expression.isBlank()) {
            return buildAutomaticKey(joinPoint, method);
        }
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
            Object value = expressionParser.parseExpression(expression).getValue(context);
            if (value == null || value.toString().isBlank()) {
                throw new BusinessException(CACHE_ERROR_CODE, "缓存键表达式结果不能为空");
            }
            return value.toString();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("解析缓存键表达式失败，方法：{}", method.toGenericString(), exception);
            throw new BusinessException(CACHE_ERROR_CODE, "解析缓存键表达式失败", exception);
        }
    }

    private String buildAutomaticKey(ProceedingJoinPoint joinPoint, Method method) {
        try {
            String argumentsJson = objectMapper.writeValueAsString(joinPoint.getArgs());
            return KEY_PREFIX + joinPoint.getTarget().getClass().getName()
                    + ":" + method.toGenericString() + ":" + argumentsJson;
        } catch (Exception exception) {
            log.error("生成自动缓存键失败，方法：{}", method.toGenericString(), exception);
            throw new BusinessException(CACHE_ERROR_CODE, "生成自动缓存键失败", exception);
        }
    }

    private Object invokeWithCache(ProceedingJoinPoint joinPoint, Method method,
                                   Cache cacheAnnotation, String key) throws Throwable {
        byte[] cachedValue = cacheUtils.get(key);
        if (cachedValue != null) {
            log.debug("命中方法缓存，方法：{}，缓存键摘要：{}",
                    method.getName(), Integer.toHexString(key.hashCode()));
            return deserialize(cachedValue, method);
        }

        log.debug("未命中方法缓存，方法：{}，缓存键摘要：{}",
                method.getName(), Integer.toHexString(key.hashCode()));
        Object result = joinPoint.proceed();
        if (result != null) {
            writeCache(key, result, cacheAnnotation.ttlSeconds(), method);
        }
        return result;
    }

    private Object deserialize(byte[] value, Method method) {
        try {
            JavaType returnType = objectMapper.getTypeFactory()
                    .constructType(method.getGenericReturnType());
            return objectMapper.readValue(value, returnType);
        } catch (Exception exception) {
            log.error("反序列化方法缓存失败，方法：{}", method.toGenericString(), exception);
            throw new BusinessException(CACHE_ERROR_CODE, "反序列化缓存数据失败", exception);
        }
    }

    private void writeCache(String key, Object result, long ttlSeconds, Method method) {
        try {
            byte[] value = objectMapper.writeValueAsBytes(result);
            if (ttlSeconds == 0) {
                cacheUtils.set(key, value);
            } else {
                cacheUtils.set(key, value, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception exception) {
            log.error("序列化方法缓存失败，方法：{}", method.toGenericString(), exception);
            throw new BusinessException(CACHE_ERROR_CODE, "序列化缓存数据失败", exception);
        }
    }
}
