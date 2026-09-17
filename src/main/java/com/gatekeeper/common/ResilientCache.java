package com.gatekeeper.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Wraps every Redis call so a Redis outage degrades latency, never correctness — callers treat
 * any failure identically to a cache miss and fall through to Postgres. See doc/scope.md §8.6.
 */
@Component
public class ResilientCache {

    private static final Logger log = LoggerFactory.getLogger(ResilientCache.class);
    private static final Duration WARN_WINDOW = Duration.ofSeconds(30);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMutex redisMutex;
    private final AtomicReference<Instant> lastWarnAt = new AtomicReference<>(Instant.EPOCH);

    public ResilientCache(RedisTemplate<String, Object> redisTemplate, RedisMutex redisMutex) {
        this.redisTemplate = redisTemplate;
        this.redisMutex = redisMutex;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        try {
            return Optional.ofNullable((T) redisTemplate.opsForValue().get(key));
        } catch (DataAccessException e) {
            warnOncePerWindow("GET", key, e);
            return Optional.empty();
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            warnOncePerWindow("SET", key, e);
        }
    }

    /** Redis down -> lock is unobtainable -> treated as "not acquired" (caller polls, then reads DB directly). */
    public boolean tryAcquireLock(String key, String token, Duration ttl) {
        try {
            return redisMutex.tryAcquire(key, token, ttl);
        } catch (DataAccessException e) {
            warnOncePerWindow("LOCK", key, e);
            return false;
        }
    }

    public void releaseLock(String key, String token) {
        try {
            redisMutex.release(key, token);
        } catch (DataAccessException e) {
            warnOncePerWindow("UNLOCK", key, e);
        }
    }

    private void warnOncePerWindow(String operation, String key, Exception e) {
        Instant now = Instant.now();
        Instant previous = lastWarnAt.get();
        if (Duration.between(previous, now).compareTo(WARN_WINDOW) >= 0
                && lastWarnAt.compareAndSet(previous, now)) {
            log.warn("Redis {} failed for key={}, falling back to Postgres (further warnings suppressed for {}s): {}",
                    operation, key, WARN_WINDOW.getSeconds(), e.toString());
        }
    }
}
