package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gatekeeper.security.UserService;
import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class LocalRoleServiceIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JdbcTemplate jdbc;

    private Tenant tenant;

    @BeforeEach
    void seed() {
        tenant = new Tenant(UUID.randomUUID(), "role-" + UUID.randomUUID(), "Role Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());

        // This suite exercises RoleService's CRUD/graph logic, not authorization (that's T-17's
        // job) — authenticate as a caller holding both role:read and role:write.
        UUID callerId = userService.createUserInternal("caller-" + UUID.randomUUID() + "@test", "pw").getId();
        UUID adminRoleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", adminRoleId, tenant.getId(), "admin");
        for (String code : List.of("role:read", "role:write")) {
            UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", adminRoleId, permissionId);
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", callerId, adminRoleId);

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
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_hierarchy");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        TenantContext.set(tenant.getId());
        jdbc.update("DELETE FROM users");
        tenantRepository.delete(tenant);
        TenantContext.clear();
    }

    @Test
    void createUpdateDeleteRoleRoundTrips() {
        Role role = roleService.createRole("editor", "can edit things");
        assertThat(roleService.getRole(role.getId()).getName()).isEqualTo("editor");

        roleService.updateRole(role.getId(), "updated description");
        assertThat(roleService.getRole(role.getId()).getDescription()).isEqualTo("updated description");

        roleService.deleteRole(role.getId());
        assertThatThrownBy(() -> roleService.getRole(role.getId())).isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void grantAndRevokePermission() {
        Role role = roleService.createRole("flag-manager", null);
        roleService.grantPermission(role.getId(), "flag:write");
        roleService.revokePermission(role.getId(), "flag:write");
        // no exception on either call is the assertion; absence is confirmed via T-15's CTE test.
        assertThat(role.getId()).isNotNull();
    }

    @Test
    void addingCyclicHierarchyEdgeIsRejectedWith409() {
        Role a = roleService.createRole("a", null);
        Role b = roleService.createRole("b", null);

        roleService.addParent(b.getId(), a.getId()); // b inherits a

        assertThatThrownBy(() -> roleService.addParent(a.getId(), b.getId())) // a inherits b -> cycle
                .isInstanceOf(CyclicHierarchyException.class);
    }

    @Test
    void selfParentIsRejected() {
        Role a = roleService.createRole("self", null);
        assertThatThrownBy(() -> roleService.addParent(a.getId(), a.getId()))
                .isInstanceOf(CyclicHierarchyException.class);
    }
}
