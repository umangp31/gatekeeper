package com.gatekeeper.rbac;

import com.gatekeeper.common.CacheKeys;
import com.gatekeeper.common.ResilientCache;
import com.gatekeeper.common.TtlJitter;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cache-aside read path for effective permissions, with a mutex-based stampede guard. See
 * doc/scope.md §8.2. Every Redis access goes through ResilientCache (T-24), so a Redis outage
 * degrades this to a direct-DB read on every call rather than failing the request — see §8.6.
 */
@Component
public class CachedPermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(CachedPermissionResolver.class);

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration TTL_JITTER = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private static final Duration MAX_POLL_WAIT = Duration.ofMillis(300);

    private final ResilientCache resilientCache;
    private final PermissionQueryRepository permissionQueryRepository;

    public CachedPermissionResolver(ResilientCache resilientCache, PermissionQueryRepository permissionQueryRepository) {
        this.resilientCache = resilientCache;
        this.permissionQueryRepository = permissionQueryRepository;
    }

    public Set<String> resolve(UUID userId, UUID tenantId) {
        String key = CacheKeys.permissions(tenantId, userId);

        var cached = this.<Set<String>>getCached(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        String lockKey = CacheKeys.lock(key);
        String token = UUID.randomUUID().toString();

        if (resilientCache.tryAcquireLock(lockKey, token, LOCK_TTL)) {
            try {
                Set<String> loaded = permissionQueryRepository.findEffectivePermissionCodes(userId, tenantId);
                resilientCache.set(key, loaded, TtlJitter.jitter(TTL, TTL_JITTER));
                return loaded;
            } finally {
                resilientCache.releaseLock(lockKey, token);
            }
        }

        return awaitOrLoadDirectly(key, userId, tenantId);
    }

    @SuppressWarnings("unchecked")
    private <T> java.util.Optional<T> getCached(String key) {
        return (java.util.Optional<T>) resilientCache.get(key);
    }

    private Set<String> awaitOrLoadDirectly(String key, UUID userId, UUID tenantId) {
        long deadline = System.currentTimeMillis() + MAX_POLL_WAIT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            var value = this.<Set<String>>getCached(key);
            if (value.isPresent()) {
                return value.get();
            }
        }
        log.debug("Cache miss lock held by another loader past poll window; reading DB directly for key={}", key);
        return permissionQueryRepository.findEffectivePermissionCodes(userId, tenantId);
    }
}
