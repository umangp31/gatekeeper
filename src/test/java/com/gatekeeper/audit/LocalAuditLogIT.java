package com.gatekeeper.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.flags.FeatureFlagService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class LocalAuditLogIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private com.gatekeeper.security.UserService userService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM feature_flags");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM tenants");
        TenantContext.clear();
    }

    @Test
    void flagUpdateWritesExactlyOneAuditRowWithCorrectActorAndPayload() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "audit-" + UUID.randomUUID(), "Audit Co");
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
        authenticateAs(callerId, tenant.getId());

        featureFlagService.createFlag("audited-flag", null);
        featureFlagService.updateFlag("audited-flag", true, 50, null, 0L);

        TenantContext.set(tenant.getId());
        List<AuditLog> entries = auditLogRepository.findByOrderByCreatedAtDesc().stream()
                .filter(e -> "FLAG_UPDATE".equals(e.getAction()))
                .toList();

        assertThat(entries).hasSize(1);
        AuditLog entry = entries.get(0);
        assertThat(entry.getActorId()).isEqualTo(callerId);
        assertThat(entry.getTarget()).isEqualTo("audited-flag");
        assertThat(entry.getPayload()).containsEntry("flagKey", "audited-flag");
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
