package com.gatekeeper.rbac;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Measurement harness for doc/scope.md §13.5 — wall-clock cost of {@code GET
 * /users/{id}/permissions} on a cache miss (recursive CTE + Redis populate) vs a cache hit
 * (single Redis GET + deserialize). Not an assertion; @Disabled so it only runs when explicitly
 * selected: {@code ./mvnw test -Dtest=LocalPermissionEndpointTimingIT -DfailIfNoTests=false}
 * with docker-compose Postgres + Redis up. Mirrors LocalIndexUsageIT's "harness, not gate" role.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Disabled("perf harness — run explicitly to refresh §13.5 numbers")
class LocalPermissionEndpointTimingIT {

    private static final String BOOTSTRAP_TOKEN = "local-dev-bootstrap-token";
    private static final String PW = "timing-harness-pw";
    private static final int WARMUP = 20;
    private static final int SAMPLES = 200;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Test
    void reportCacheMissVsHitLatency() {
        String slug = "timing-" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin@" + slug + ".test";
        bootstrap(slug, adminEmail);
        String token = login(slug, adminEmail);
        HttpHeaders auth = new HttpHeaders();
        auth.set("Authorization", "Bearer " + token);
        HttpEntity<Void> req = new HttpEntity<>(auth);

        // The admin user id — it holds all 8 catalogue permissions through the bootstrapped
        // admin role, so the CTE walks a real (if shallow) reachable set.
        UUID adminId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, adminEmail);
        String path = "/api/v1/users/" + adminId + "/permissions";
        String permKey = "gk:perm:" + jdbc.queryForObject(
                "SELECT tenant_id FROM users WHERE id = ?", UUID.class, adminId) + ":" + adminId;

        List<Long> miss = new ArrayList<>();
        List<Long> hit = new ArrayList<>();
        for (int i = 0; i < WARMUP + SAMPLES; i++) {
            redisTemplate.delete(permKey);
            long t0 = System.nanoTime();
            restTemplate.exchange(path, HttpMethod.GET, req, String.class);
            long missNanos = System.nanoTime() - t0;

            long t1 = System.nanoTime();
            restTemplate.exchange(path, HttpMethod.GET, req, String.class);
            long hitNanos = System.nanoTime() - t1;

            if (i >= WARMUP) {
                miss.add(missNanos);
                hit.add(hitNanos);
            }
        }
        System.out.printf("%n=== §13.5  GET /users/{id}/permissions  (n=%d) ===%n", SAMPLES);
        System.out.printf("cache miss (CTE + Redis populate) : p50 %.2f ms   p95 %.2f ms%n",
                ms(percentile(miss, 50)), ms(percentile(miss, 95)));
        System.out.printf("cache hit  (Redis GET + deserialize): p50 %.2f ms   p95 %.2f ms%n",
                ms(percentile(hit, 50)), ms(percentile(hit, 95)));
    }

    private void bootstrap(String slug, String adminEmail) {
        HttpHeaders h = new HttpHeaders();
        h.set("Content-Type", "application/json");
        h.set("X-Bootstrap-Token", BOOTSTRAP_TOKEN);
        String body = """
                {"slug":"%s","name":"Timing Co","adminEmail":"%s","adminPassword":"%s"}
                """.formatted(slug, adminEmail, PW);
        restTemplate.postForEntity("/api/v1/tenants", new HttpEntity<>(body, h), String.class);
    }

    private String login(String slug, String email) {
        HttpHeaders h = new HttpHeaders();
        h.set("Content-Type", "application/json");
        String body = """
                {"tenantSlug":"%s","email":"%s","password":"%s"}
                """.formatted(slug, email, PW);
        String json = restTemplate.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(body, h), String.class).getBody();
        int s = json.indexOf("\"accessToken\":\"") + 15;
        return json.substring(s, json.indexOf('"', s));
    }

    private static long percentile(List<Long> values, int p) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        return sorted.get((int) Math.ceil(p / 100.0 * sorted.size()) - 1);
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }
}
