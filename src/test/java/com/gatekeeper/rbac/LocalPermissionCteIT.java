package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §10 T8 (CTE tenant isolation), T10 (hierarchy correctness), T11
 * (cycle termination) — see T-05 note.
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalPermissionCteIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PermissionQueryRepository permissionQueryRepository;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_hierarchy");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM tenants");
        TenantContext.clear();
    }

    @Test
    void threeLevelHierarchyGrantsRootPermissionToLeaf() {
        Tenant tenant = newTenant();
        UUID userId = insertUser(tenant.getId());

        UUID rootRole = insertRole(tenant.getId(), "root");
        UUID midRole = insertRole(tenant.getId(), "mid");
        UUID leafRole = insertRole(tenant.getId(), "leaf");
        UUID permissionId = permissionId("flag:write");

        grantPermission(rootRole, permissionId);
        addHierarchyEdge(rootRole, midRole); // mid inherits root
        addHierarchyEdge(midRole, leafRole); // leaf inherits mid
        assignRole(userId, leafRole);

        Set<String> effective = permissionQueryRepository.findEffectivePermissionCodes(userId, tenant.getId());

        assertThat(effective).contains("flag:write");
    }

    @Test
    void cycleInHierarchyTerminatesAndReturnsCorrectSet() {
        Tenant tenant = newTenant();
        UUID userId = insertUser(tenant.getId());

        UUID roleA = insertRole(tenant.getId(), "a");
        UUID roleB = insertRole(tenant.getId(), "b");
        UUID permissionId = permissionId("role:read");

        grantPermission(roleA, permissionId);
        addHierarchyEdge(roleA, roleB); // b inherits a
        addHierarchyEdge(roleB, roleA); // a inherits b - cycle
        assignRole(userId, roleA);

        Set<String> effective = permissionQueryRepository.findEffectivePermissionCodes(userId, tenant.getId());

        assertThat(effective).containsExactly("role:read");
    }

    @Test
    void permissionsGrantedInAnotherTenantDoNotLeak() {
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();
        UUID userA = insertUser(tenantA.getId());

        UUID roleInB = insertRole(tenantB.getId(), "b-only-role");
        UUID secretPermission = permissionId("tenant:admin");
        grantPermission(roleInB, secretPermission);
        // userA is never assigned roleInB, and roleInB belongs to tenant B - the CTE's anchor
        // is scoped by tenantId, so even a stray cross-tenant assignment couldn't leak it.
        assignRole(userA, roleInB);

        Set<String> effective = permissionQueryRepository.findEffectivePermissionCodes(userA, tenantA.getId());

        assertThat(effective).doesNotContain("tenant:admin");
    }

    private Tenant newTenant() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "cte-" + UUID.randomUUID(), "CTE Co");
        tenantRepository.saveAndFlush(tenant);
        return tenant;
    }

    private UUID insertUser(UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, password_hash) VALUES (?,?,?,?)",
                id, tenantId, "u-" + id + "@test", "hash");
        return id;
    }

    private UUID insertRole(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", id, tenantId, name + "-" + id);
        return id;
    }

    private UUID permissionId(String code) {
        return jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
    }

    private void grantPermission(UUID roleId, UUID permissionId) {
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);
    }

    private void addHierarchyEdge(UUID parentRoleId, UUID childRoleId) {
        jdbc.update("INSERT INTO role_hierarchy (parent_role_id, child_role_id) VALUES (?,?)", parentRoleId, childRoleId);
    }

    private void assignRole(UUID userId, UUID roleId) {
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);
    }
}
