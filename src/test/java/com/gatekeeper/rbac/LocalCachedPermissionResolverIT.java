package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.common.ResilientCache;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §10 T13 (cache stampede), see T-05 note. Uses a Mockito-counted DB
 * loader wired to the REAL Redis (docker-compose) via RedisTemplate/RedisMutex, so the mutex
 * and TTL logic run for real — only the "Postgres" side is a spy to make the assertion cheap.
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalCachedPermissionResolverIT {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ResilientCache resilientCache;

    @Test
    void fiftyConcurrentMissesTriggerAtMostTwoDbLoads() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Set<String> expected = new java.util.LinkedHashSet<>(List.of("flag:read", "flag:write"));

        AtomicInteger loadCount = new AtomicInteger();
        PermissionQueryRepository countingRepository = new PermissionQueryRepository() {
            @Override
            public Set<String> findEffectivePermissionCodes(UUID queriedUserId, UUID queriedTenantId) {
                loadCount.incrementAndGet();
                try {
                    Thread.sleep(100); // simulate real DB latency so concurrent callers actually overlap
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return expected;
            }
        };

        CachedPermissionResolver resolver = new CachedPermissionResolver(resilientCache, countingRepository);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<java.util.concurrent.Future<Set<String>>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return resolver.resolve(userId, tenantId);
            }));
        }

        ready.await();
        go.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (var future : futures) {
            assertThat(future.get()).isEqualTo(expected);
        }
        assertThat(loadCount.get()).isLessThanOrEqualTo(2);

        redisTemplate.delete("gk:perm:" + tenantId + ":" + userId);
    }
}
