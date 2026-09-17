package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §10 T12 (cache invalidation), see T-05 note: granting a permission is
 * visible on the very next request, without waiting for the cache TTL to expire.
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalCacheInvalidationIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RoleService roleService;

    @Autowired
    private CachedPermissionResolver cachedPermissionResolver;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_hierarchy");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM tenants");
        TenantContext.clear();
    }

    @Test
    void grantingPermissionInvalidatesCacheImmediately() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "inval-" + UUID.randomUUID(), "Inval Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());

        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, password_hash) VALUES (?,?,?,?)",
                userId, tenant.getId(), "u-" + userId + "@test", "hash");

        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenant.getId(), "role");
        UUID roleWritePermId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, "role:write");
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, roleWritePermId);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);

        // Prime the cache: this user does NOT have flag:write yet.
        assertThat(cachedPermissionResolver.resolve(userId, tenant.getId())).doesNotContain("flag:write");

        authenticateAs(userId, tenant.getId());
        roleService.grantPermission(roleId, "flag:write");
        SecurityContextHolder.clearContext();

        // No sleep, no TTL wait: pub/sub invalidation is asynchronous, so poll briefly (well
        // under the 10-minute TTL) rather than assert instantaneous propagation.
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(cachedPermissionResolver.resolve(userId, tenant.getId())).contains("flag:write"));
    }

    private void authenticateAs(UUID userId, UUID tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("tenant", tenantId.toString())
                .claim("attrs", Map.of())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
