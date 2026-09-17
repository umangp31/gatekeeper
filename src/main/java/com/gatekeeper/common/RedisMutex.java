package com.gatekeeper.common;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed mutex on top of Redis SET NX PX. Release is check-and-delete via a Lua script so
 * a holder never releases a lock it doesn't own (e.g. after its own TTL expired and someone
 * else acquired it). See doc/scope.md §8.2.
 */
@Component
public class RedisMutex {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisMutex(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String key, String token, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(String key, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
    }
}
