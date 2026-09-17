package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §10 T14 (Redis down -> requests still succeed from Postgres), see T-05
 * note. Stops the actual docker-compose Redis container via the docker CLI (Testcontainers'
 * Java client is blocked in this sandbox, but the CLI itself works fine — see T-05).
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalRedisDegradationIT {

    private static final String REDIS_CONTAINER = "gatekeeper-redis";

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CachedPermissionResolver cachedPermissionResolver;

    @AfterEach
    void cleanup() throws Exception {
        jdbc.update("DELETE FROM audit_log");
        startContainer(); // safety net in case an assertion failed mid-test
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM tenants");
        TenantContext.clear();
    }

    @Test
    void resolvesPermissionsFromPostgresWhenRedisIsDown() throws Exception {
        Tenant tenant = new Tenant(UUID.randomUUID(), "degrade-" + UUID.randomUUID(), "Degrade Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());

        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, password_hash) VALUES (?,?,?,?)",
                userId, tenant.getId(), "u-" + userId + "@test", "hash");
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenant.getId(), "role");
        UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, "flag:read");
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);

        stopContainer();
        try {
            var result = cachedPermissionResolver.resolve(userId, tenant.getId());
            assertThat(result).contains("flag:read");
        } finally {
            startContainer();
        }
    }

    private void stopContainer() throws Exception {
        run("docker", "stop", REDIS_CONTAINER);
    }

    private void startContainer() throws Exception {
        run("docker", "start", REDIS_CONTAINER);
        // give the container a moment to accept connections again before the next test runs
        Thread.sleep(1000);
    }

    private void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command));
        }
    }
}
