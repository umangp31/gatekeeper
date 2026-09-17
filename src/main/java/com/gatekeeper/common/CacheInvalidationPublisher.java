package com.gatekeeper.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes cache-key invalidation on the "gatekeeper:invalidate" channel — every instance
 * (including the publisher) reacts via CacheInvalidationListener, so eviction always happens
 * through one code path. Callers must use afterCommit(), never invalidate mid-transaction: a
 * concurrent reader could otherwise repopulate the cache from the not-yet-committed old state,
 * leaving a stale entry with a full TTL ahead of it. See doc/scope.md §8.3.
 */
@Component
public class CacheInvalidationPublisher {

    public static final String CHANNEL = "gatekeeper:invalidate";

    private final StringRedisTemplate stringRedisTemplate;

    public CacheInvalidationPublisher(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** Publishes after the current transaction commits. No-op (publishes immediately) if none is active. */
    public void afterCommitEvict(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(key);
            }
        });
    }

    private void publish(String key) {
        stringRedisTemplate.convertAndSend(CHANNEL, key);
    }
}
