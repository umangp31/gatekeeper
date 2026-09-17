package com.gatekeeper.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter keyed by tenantSlug+email+clientIp: 10 attempts per 60s window (doc/
 * scope.md §9). Must fail OPEN if Redis is unavailable — availability of the login path
 * outranks throttling here, matching the degrade-never-block posture from §8.6.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;

    public LoginRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** Throws RateLimitExceededException if the caller has exceeded MAX_ATTEMPTS in the current window. */
    public void checkAllowed(String tenantSlug, String email, String clientIp) {
        String key = "gk:ratelimit:login:" + tenantSlug + ":" + email + ":" + clientIp;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, WINDOW);
            }
            if (count != null && count > MAX_ATTEMPTS) {
                Long ttl = stringRedisTemplate.getExpire(key);
                throw new RateLimitExceededException(ttl != null && ttl > 0 ? ttl : WINDOW.getSeconds());
            }
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for login rate limiting, failing open: {}", e.toString());
        }
    }
}
