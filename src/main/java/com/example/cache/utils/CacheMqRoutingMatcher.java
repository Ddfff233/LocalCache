package com.example.cache.utils;

import com.example.cache.domain.enums.CacheMqExchangeType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 匹配本地 MQ 的 Exchange 路由规则。
 */
public class CacheMqRoutingMatcher {

    public boolean matches(CacheMqExchangeType type, String bindingKey, String routingKey) {
        Objects.requireNonNull(type, "Exchange 类型不能为空");
        String actualBindingKey = bindingKey == null ? "" : bindingKey;
        String actualRoutingKey = routingKey == null ? "" : routingKey;
        return switch (type) {
            case DIRECT -> actualBindingKey.equals(actualRoutingKey);
            case FANOUT -> true;
            case TOPIC -> matchTopic(split(actualBindingKey), split(actualRoutingKey),
                    0, 0, new HashMap<>());
        };
    }

    private boolean matchTopic(String[] pattern, String[] routingKey,
                               int patternIndex, int routingIndex,
                               Map<String, Boolean> memo) {
        String memoKey = patternIndex + ":" + routingIndex;
        Boolean cached = memo.get(memoKey);
        if (cached != null) {
            return cached;
        }

        boolean matched;
        if (patternIndex == pattern.length) {
            matched = routingIndex == routingKey.length;
        } else if ("#".equals(pattern[patternIndex])) {
            matched = matchTopic(pattern, routingKey, patternIndex + 1, routingIndex, memo)
                    || routingIndex < routingKey.length
                    && matchTopic(pattern, routingKey, patternIndex, routingIndex + 1, memo);
        } else {
            matched = routingIndex < routingKey.length
                    && ("*".equals(pattern[patternIndex])
                    || pattern[patternIndex].equals(routingKey[routingIndex]))
                    && matchTopic(pattern, routingKey, patternIndex + 1,
                    routingIndex + 1, memo);
        }
        memo.put(memoKey, matched);
        return matched;
    }

    private String[] split(String value) {
        return value.isEmpty() ? new String[0] : value.split("\\.", -1);
    }
}
