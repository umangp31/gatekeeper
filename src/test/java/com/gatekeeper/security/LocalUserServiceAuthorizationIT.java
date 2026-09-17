package com.gatekeeper.security;

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

/** T-18: proves @PreAuthorize("hasPermission('user','write'|'read')") is enforced on UserService. */
@SpringBootTest
@ActiveProfiles("local")
class LocalUserServiceAuthorizationIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

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
    void createUserDeniedWithoutUserWritePermission() {
        Tenant tenant = newTenant();
        TenantContext.set(tenant.getId());
        UUID callerId = userService.createUserInternal("caller-" + UUID.randomUUID() + "@test", "pw").getId();
        authenticateAs(callerId, tenant.getId());

        assertThatThrownBy(() -> userService.createUser("new-" + UUID.randomUUID() + "@test", "pw"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createUserAllowedWithUserWritePermission() {
        Tenant tenant = newTenant();
        TenantContext.set(tenant.getId());
        UUID callerId = userService.createUserInternal("caller-" + UUID.randomUUID() + "@test", "pw").getId();
        grantPermissionViaRole(tenant.getId(), callerId, "user:write");
        authenticateAs(callerId, tenant.getId());

        var created = userService.createUser("new-" + UUID.randomUUID() + "@test", "pw");
        assertThat(created.getId()).isNotNull();
    }

    private Tenant newTenant() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "userauth-" + UUID.randomUUID(), "UserAuth Co");
        tenantRepository.saveAndFlush(tenant);
        return tenant;
    }

    private void grantPermissionViaRole(UUID tenantId, UUID userId, String code) {
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenantId, "role-" + roleId);
        UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);
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
