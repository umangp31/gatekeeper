package com.gatekeeper.flags;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.security.UserService;
import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class LocalCachedFlagRepositoryIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private CachedFlagRepository cachedFlagRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Tenant tenant;

    @BeforeEach
    void seed() {
        tenant = new Tenant(UUID.randomUUID(), "cflag-" + UUID.randomUUID(), "CFlag Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());

        UUID callerId = userService.createUserInternal("caller-" + UUID.randomUUID() + "@test", "pw").getId();
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenant.getId(), "flag-admin");
        for (String code : List.of("flag:read", "flag:write")) {
            UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", callerId, roleId);

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(callerId.toString())
                .claim("tenant", tenant.getId().toString())
                .claim("attrs", Map.of())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM flag_whitelist");
        jdbc.update("DELETE FROM flag_environment_override");
        jdbc.update("DELETE FROM feature_flags");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM users");
        TenantContext.set(tenant.getId());
        tenantRepository.delete(tenant);
        TenantContext.clear();
    }

    @Test
    void unknownKeyConsistentlyReturnsEmptyViaNegativeCache() {
        String unknownKey = "no-such-flag-" + UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            assertThat(cachedFlagRepository.getFlag(unknownKey, tenant.getId())).isEmpty();
        }
    }

    @Test
    void updatingAFlagIsReflectedInCacheWithoutWaitingForTtl() {
        featureFlagService.createFlag("cache-flag", null);

        // Prime the cache with the "disabled" state.
        assertThat(cachedFlagRepository.getFlag("cache-flag", tenant.getId()))
                .hasValueSatisfying(f -> assertThat(f.enabled()).isFalse());

        featureFlagService.updateFlag("cache-flag", true, 100, null, 0L);

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(cachedFlagRepository.getFlag("cache-flag", tenant.getId()))
                        .hasValueSatisfying(f -> assertThat(f.enabled()).isTrue()));
    }
}
