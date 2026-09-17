package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sandbox stand-in for §10 T19 (index usage), see T-05 note. See doc/scope.md §13 for the full
 * writeup: at small-to-medium data volumes Postgres's own cost-based planner often (correctly)
 * prefers a sequential scan over the composite/covering indexes, even though forcing the
 * indexes on measurably wins on wall-clock time. So this test forces enable_seqscan=off
 * (deterministic, data-volume-independent) to prove the §4.1 indexes are actually usable by
 * the CTE's exact query shape — not that the planner always picks them unprompted.
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalIndexUsageIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_hierarchy");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM tenants");
        TenantContext.clear();
    }

    @Test
    @Transactional
    void indexesAreUsableByTheCteWhenForced() {
        jdbc.execute("SET LOCAL enable_seqscan = off");

        Tenant tenant = new Tenant(UUID.randomUUID(), "idx-" + UUID.randomUUID(), "Idx Co");
        tenantRepository.saveAndFlush(tenant);

        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, password_hash) VALUES (?,?,?,?)",
                userId, tenant.getId(), "u-" + userId + "@test", "hash");
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenant.getId(), "role");
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);
        UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, "flag:read");
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);

        List<String> planLines = jdbc.queryForList("""
                EXPLAIN (ANALYZE, FORMAT TEXT)
                WITH RECURSIVE reachable_roles(role_id, depth) AS (
                    SELECT ur.role_id, 0
                    FROM user_roles ur
                    JOIN roles r ON r.id = ur.role_id
                    WHERE ur.user_id = ? AND r.tenant_id = ?
                    UNION
                    SELECT rh.parent_role_id, rr.depth + 1
                    FROM role_hierarchy rh
                    JOIN reachable_roles rr ON rr.role_id = rh.child_role_id
                    WHERE rr.depth < 32
                )
                SELECT DISTINCT p.code
                FROM reachable_roles rr
                JOIN role_permissions rp ON rp.role_id = rr.role_id
                JOIN permissions p ON p.id = rp.permission_id
                """, String.class, userId, tenant.getId());

        String plan = String.join("\n", planLines);
        assertThat(plan)
                .as("with enable_seqscan=off, every base-table access must go through an index; "
                        + "see doc/scope.md §13 for why the planner's own unforced choice differs")
                .doesNotContain("Seq Scan on user_roles")
                .doesNotContain("Seq Scan on roles")
                .doesNotContain("Seq Scan on role_permissions")
                .doesNotContain("Seq Scan on role_hierarchy")
                .contains("Index");
    }
}
