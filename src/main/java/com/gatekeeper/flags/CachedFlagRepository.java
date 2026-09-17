package com.gatekeeper.flags;

import com.gatekeeper.common.CacheKeys;
import com.gatekeeper.common.ResilientCache;
import com.gatekeeper.common.TtlJitter;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cache-aside for the flag row itself (enabled, rolloutPercentage) — reuses the same
 * mutex/poll-then-DB-fallback shape as CachedPermissionResolver (T-22), plus negative caching
 * for unknown keys so a misconfigured client polling a typo'd flag doesn't hammer Postgres on
 * every request. See doc/scope.md §8.1, §8.5.
 */
@Component
public class CachedFlagRepository {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration TTL_JITTER = Duration.ofSeconds(30);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private static final Duration MAX_POLL_WAIT = Duration.ofMillis(300);

    private final ResilientCache resilientCache;
    private final FeatureFlagRepository featureFlagRepository;

    public CachedFlagRepository(ResilientCache resilientCache, FeatureFlagRepository featureFlagRepository) {
        this.resilientCache = resilientCache;
        this.featureFlagRepository = featureFlagRepository;
    }

    public Optional<CachedFlag> getFlag(String flagKey, UUID tenantId) {
        String key = CacheKeys.flag(tenantId, flagKey);

        Optional<CachedFlag> cached = resilientCache.get(key);
        if (cached.isPresent()) {
            return toResult(cached.get());
        }

        String lockKey = CacheKeys.lock(key);
        String token = UUID.randomUUID().toString();

        if (resilientCache.tryAcquireLock(lockKey, token, LOCK_TTL)) {
            try {
                return toResult(loadAndCache(flagKey, tenantId, key));
            } finally {
                resilientCache.releaseLock(lockKey, token);
            }
        }

        return awaitOrLoadDirectly(key, flagKey, tenantId);
    }

    private CachedFlag loadAndCache(String flagKey, UUID tenantId, String key) {
        Optional<FeatureFlag> dbFlag = featureFlagRepository.findByFlagKeyAndTenantId(flagKey, tenantId);
        CachedFlag cachedFlag = dbFlag.map(CachedFlag::of).orElseGet(CachedFlag::missing);
        Duration ttl = dbFlag.isPresent() ? TtlJitter.jitter(TTL, TTL_JITTER) : NEGATIVE_TTL;
        resilientCache.set(key, cachedFlag, ttl);
        return cachedFlag;
    }

    private Optional<CachedFlag> awaitOrLoadDirectly(String key, String flagKey, UUID tenantId) {
        long deadline = System.currentTimeMillis() + MAX_POLL_WAIT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Optional<CachedFlag> value = resilientCache.get(key);
            if (value.isPresent()) {
                return toResult(value.get());
            }
        }
        return toResult(loadAndCache(flagKey, tenantId, key));
    }

    private Optional<CachedFlag> toResult(CachedFlag cachedFlag) {
        return cachedFlag.exists() ? Optional.of(cachedFlag) : Optional.empty();
    }
}
