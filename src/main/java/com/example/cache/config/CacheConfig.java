package com.example.cache.config;

import com.example.cache.domain.cache.CacheValue;
import com.example.cache.domain.entity.CacheEntry;
import com.example.cache.service.CacheEntryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 提供读穿透和关闭回写能力的本地持久化缓存引擎。
 */
@Configuration
public class CacheConfig implements DisposableBean {

    private final CacheEntryService cacheEntryService;
    private final Clock clock;
    private final Object lifecycleLock = new Object();
    private final Map<String, PendingChange> pendingChanges = new HashMap<>();
    private final Cache<String, CacheValue> cache;

    @Autowired
    public CacheConfig(CacheEntryService cacheEntryService,
                       @Value("${cache.max-size}") long maximumSize) {
        this(cacheEntryService, maximumSize, Clock.systemUTC(), Ticker.systemTicker());
    }

    CacheConfig(CacheEntryService cacheEntryService, long maximumSize, Clock clock, Ticker ticker) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("缓存最大容量必须大于零");
        }
        this.cacheEntryService = Objects.requireNonNull(cacheEntryService, "缓存持久化服务不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(Objects.requireNonNull(ticker, "计时器不能为空"))
                .expireAfter(new PerEntryExpiry(clock))
                .executor(Runnable::run)
                .removalListener(this::handleRemoval)
                .recordStats()
                .build();
    }

    /**
     * 加载有效的持久化条目，并登记需要删除的过期数据。
     */
    @PostConstruct
    public void loadCacheFromSqlite() {
        synchronized (lifecycleLock) {
            long now = clock.millis();
            for (CacheEntry entry : cacheEntryService.list()) {
                if (isExpired(entry.getExpireAt(), now)) {
                    pendingChanges.put(entry.getKey(), PendingChange.delete());
                    continue;
                }
                cache.put(entry.getKey(), toCacheValue(entry));
            }
        }
    }

    public byte[] get(String key) {
        validateKey(key);
        synchronized (lifecycleLock) {
            CacheValue value = findValue(key);
            return value == null ? null : value.getValue();
        }
    }

    public void set(String key, byte[] value, Duration ttl) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("缓存值不能为空");
        }
        validateTtl(ttl);

        synchronized (lifecycleLock) {
            long now = clock.millis();
            CacheValue current = findValue(key);
            long createAt = current == null ? now : current.getCreateAt();
            CacheValue replacement = new CacheValue(
                    value, calculateExpireAt(now, ttl), createAt, now);
            cache.put(key, replacement);
            pendingChanges.put(key, PendingChange.upsert(replacement));
        }
    }

    public boolean delete(String key) {
        validateKey(key);
        synchronized (lifecycleLock) {
            if (findValue(key) == null) {
                return false;
            }
            pendingChanges.put(key, PendingChange.delete());
            cache.invalidate(key);
            return true;
        }
    }

    public boolean hasKey(String key) {
        validateKey(key);
        synchronized (lifecycleLock) {
            return findValue(key) != null;
        }
    }

    public boolean expire(String key, Duration ttl) {
        validateKey(key);
        validateTtl(ttl);
        synchronized (lifecycleLock) {
            CacheValue current = findValue(key);
            if (current == null) {
                return false;
            }
            long now = clock.millis();
            CacheValue replacement = new CacheValue(
                    current.getValue(), calculateExpireAt(now, ttl), current.getCreateAt(), now);
            cache.put(key, replacement);
            pendingChanges.put(key, PendingChange.upsert(replacement));
            return true;
        }
    }

    public Duration getExpire(String key) {
        validateKey(key);
        synchronized (lifecycleLock) {
            CacheValue value = findValue(key);
            if (value == null) {
                return null;
            }
            if (value.getExpireAt() == null) {
                return Duration.ZERO;
            }
            return Duration.ofMillis(Math.max(0, value.getExpireAt() - clock.millis()));
        }
    }

    void cleanUp() {
        synchronized (lifecycleLock) {
            cache.cleanUp();
        }
    }

    @Override
    public void destroy() {
        synchronized (lifecycleLock) {
            cache.cleanUp();
            long now = clock.millis();
            List<CacheEntry> upserts = new ArrayList<>();
            List<String> deletes = new ArrayList<>();

            pendingChanges.forEach((key, change) -> {
                if (change.type() == ChangeType.DELETE || isExpired(change.value().getExpireAt(), now)) {
                    deletes.add(key);
                } else {
                    upserts.add(toCacheEntry(key, change.value()));
                }
            });

            cacheEntryService.applyChanges(upserts, deletes);
            pendingChanges.clear();
        }
    }

    private CacheValue findValue(String key) {
        long now = clock.millis();
        CacheValue cached = cache.getIfPresent(key);
        if (cached != null) {
            return getUnexpiredValue(key, cached, now);
        }

        PendingChange pending = pendingChanges.get(key);
        if (pending != null) {
            if (pending.type() == ChangeType.DELETE) {
                return null;
            }
            CacheValue value = getUnexpiredValue(key, pending.value(), now);
            if (value != null) {
                cache.put(key, value);
            }
            return value;
        }

        CacheEntry entry = cacheEntryService.getById(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry.getExpireAt(), now)) {
            pendingChanges.put(key, PendingChange.delete());
            return null;
        }

        CacheValue value = toCacheValue(entry);
        cache.put(key, value);
        return value;
    }

    private CacheValue getUnexpiredValue(String key, CacheValue value, long now) {
        if (!isExpired(value.getExpireAt(), now)) {
            return value;
        }
        pendingChanges.put(key, PendingChange.delete());
        cache.invalidate(key);
        return null;
    }

    private void handleRemoval(String key, CacheValue value, RemovalCause cause) {
        if (key == null || value == null || cause != RemovalCause.EXPIRED) {
            return;
        }
        synchronized (lifecycleLock) {
            pendingChanges.put(key, PendingChange.delete());
        }
    }

    private CacheValue toCacheValue(CacheEntry entry) {
        return new CacheValue(entry.getValue(), entry.getExpireAt(),
                entry.getCreateAt(), entry.getUpdateAt());
    }

    private CacheEntry toCacheEntry(String key, CacheValue value) {
        CacheEntry entry = new CacheEntry();
        entry.setKey(key);
        entry.setValue(value.getValue());
        entry.setExpireAt(value.getExpireAt());
        entry.setCreateAt(value.getCreateAt());
        entry.setUpdateAt(value.getUpdateAt());
        return entry;
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("缓存键不能为空");
        }
    }

    private static void validateTtl(Duration ttl) {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("缓存有效期必须大于零");
        }
    }

    private static Long calculateExpireAt(long now, Duration ttl) {
        if (ttl == null) {
            return null;
        }
        try {
            long ttlMillis = Math.max(1, ttl.toMillis());
            return Math.addExact(now, ttlMillis);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("缓存有效期超出支持范围", exception);
        }
    }

    private static boolean isExpired(Long expireAt, long now) {
        return expireAt != null && expireAt <= now;
    }

    private enum ChangeType {
        UPSERT,
        DELETE
    }

    private record PendingChange(ChangeType type, CacheValue value) {

        private static PendingChange upsert(CacheValue value) {
            return new PendingChange(ChangeType.UPSERT, value);
        }

        private static PendingChange delete() {
            return new PendingChange(ChangeType.DELETE, null);
        }
    }

    private static final class PerEntryExpiry implements Expiry<String, CacheValue> {

        private final Clock clock;

        private PerEntryExpiry(Clock clock) {
            this.clock = clock;
        }

        @Override
        public long expireAfterCreate(String key, CacheValue value, long currentTime) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterUpdate(String key, CacheValue value,
                                      long currentTime, long currentDuration) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterRead(String key, CacheValue value,
                                    long currentTime, long currentDuration) {
            return remainingNanos(value);
        }

        private long remainingNanos(CacheValue value) {
            if (value.getExpireAt() == null) {
                return Long.MAX_VALUE;
            }
            long remainingMillis = Math.max(0, value.getExpireAt() - clock.millis());
            return TimeUnit.MILLISECONDS.toNanos(remainingMillis);
        }
    }
}
