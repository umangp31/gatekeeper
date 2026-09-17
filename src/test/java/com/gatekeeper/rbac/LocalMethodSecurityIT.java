package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §10 T5 (authorization failure -> 403 / AccessDeniedException), see T-05
 * note. Proves the custom PermissionEvaluator + expression handler wired via @PreAuthorize.
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalMethodSecurityIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM tenants");
        TenantContext.clear();
    }

    @Test
    void deniesWithoutPermission() {
        Tenant tenant = newTenant();
        UUID userId = insertUser(tenant.getId());
        authenticateAs(userId, tenant.getId(), Map.of());
        TenantContext.set(tenant.getId());

        assertThatThrownBy(() -> roleService.createRole("editor", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allowsWithGrantedPermission() {
        Tenant tenant = newTenant();
        UUID userId = insertUser(tenant.getId());

        // Grant role:write via a role assigned to the user, using an unauthenticated bootstrap
        // context (roleService.createRole itself now requires the permission, so this bootstrap
        // role/permission plumbing goes straight through the graph repository).
        TenantContext.set(tenant.getId());
        UUID adminRoleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", adminRoleId, tenant.getId(), "admin");
        UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, "role:write");
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", adminRoleId, permissionId);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, adminRoleId);

        authenticateAs(userId, tenant.getId(), Map.of());

        Role created = roleService.createRole("editor", null);
        assertThat(created.getName()).isEqualTo("editor");
    }

    private Tenant newTenant() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "methsec-" + UUID.randomUUID(), "MethSec Co");
        tenantRepository.saveAndFlush(tenant);
        return tenant;
    }

    private UUID insertUser(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, password_hash) VALUES (?,?,?,?)",
                id, tenantId, "u-" + id + "@test", "hash");
        return id;
    }

    private void authenticateAs(UUID userId, UUID tenantId, Map<String, Object> attrs) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("tenant", tenantId.toString())
                .claim("attrs", attrs)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
