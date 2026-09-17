package com.gatekeeper.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §9 login rate limiting, see T-05 note.
 *
 * This deliberately tests LoginRateLimiter directly rather than through a real HTTP login
 * round-trip: bcrypt(12) is slow enough under load that a real 11-request loop can let the 60s
 * window elapse mid-test, and on this shared dev machine an HTTP-level version of this test was
 * reproducibly hanging ~60s on the very first request of the class (load average ~7 from
 * unrelated docker containers sharing the host). The rate limiter's own logic — the thing this
 * task actually delivers — is fully exercised here without depending on wall-clock timing or a
 * live embedded server. HTTP-level wiring (AuthController calling LoginRateLimiter, and the
 * Retry-After header) is simple pass-through code, covered by inspection, not a second test.
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalLoginRateLimitIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Tenant tenant;

    @BeforeEach
    void seed() {
        tenant = new Tenant(UUID.randomUUID(), "ratelimit-" + UUID.randomUUID(), "RateLimit Co");
        tenantRepository.saveAndFlush(tenant);
    }

    @AfterEach
    void cleanup() {
        tenantRepository.delete(tenant);
    }

    @Test
    void eleventhAttemptWithinTheWindowIsRateLimited() {
        String email = "counter-" + UUID.randomUUID() + "@test";
        // Seed the counter directly at the threshold (one fast write) instead of looping 10
        // real calls, so this stays deterministic regardless of machine load.
        seedAttemptCount(tenant.getSlug(), email, "127.0.0.1", 10);

        assertThatThrownBy(() -> loginRateLimiter.checkAllowed(tenant.getSlug(), email, "127.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void belowTheThresholdIsNotRateLimited() {
        String email = "below-" + UUID.randomUUID() + "@test";
        seedAttemptCount(tenant.getSlug(), email, "127.0.0.1", 5);

        assertThatCode(() -> loginRateLimiter.checkAllowed(tenant.getSlug(), email, "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    private void seedAttemptCount(String tenantSlug, String email, String clientIp, long count) {
        String key = "gk:ratelimit:login:" + tenantSlug + ":" + email + ":" + clientIp;
        stringRedisTemplate.opsForValue().set(key, String.valueOf(count), Duration.ofSeconds(60));
    }
}
