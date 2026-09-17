package com.gatekeeper.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.security.User;
import com.gatekeeper.support.AbstractIntegrationTest;
import com.gatekeeper.security.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * §10 T6 (tenant leakage - API), on Testcontainers. For every id-addressed
 * endpoint, tenant A's token requesting tenant B's resource must get 404 — never 403, which
 * would confirm the resource exists in another tenant (doc/scope.md §9).
 */
class TenantLeakageIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbc;

    private Tenant tenantA;
    private Tenant tenantB;
    private User userA;
    private User userB;
    private UUID roleBId;
    private String tokenA;

    @BeforeEach
    void seed() {
        tenantA = newTenant("leak-a");
        tenantB = newTenant("leak-b");

        TenantContext.set(tenantA.getId());
        userA = userService.createUserInternal("a-" + UUID.randomUUID() + "@test", RAW_PASSWORD);
        grantAllReadWrite(tenantA.getId(), userA.getId());

        TenantContext.set(tenantB.getId());
        userB = userService.createUserInternal("b-" + UUID.randomUUID() + "@test", RAW_PASSWORD);
        grantAllReadWrite(tenantB.getId(), userB.getId());
        roleBId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleBId, tenantB.getId(), "b-only-role");
        TenantContext.clear();

        tokenA = login(tenantA.getSlug(), userA.getEmail());
    }


    @Test
    void userByIdAcrossTenantsReturns404NotForbidden() {
        var response = getWithBearer("/api/v1/users/" + userB.getId(), tokenA);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void roleByIdAcrossTenantsReturns404NotForbidden() {
        var response = getWithBearer("/api/v1/roles/" + roleBId, tokenA);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listUsersFromTenantANeverIncludesTenantBUsers() {
        var response = getWithBearer("/api/v1/users", tokenA);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(userB.getId().toString());
    }

    @Test
    void listRolesFromTenantANeverIncludesTenantBRoles() {
        var response = getWithBearer("/api/v1/roles", tokenA);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(roleBId.toString());
    }

    @Test
    void grantingPermissionOnAnotherTenantsRoleReturns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenA);
        var response = restTemplate.exchange(
                "/api/v1/roles/" + roleBId + "/permissions/flag:write",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void effectivePermissionsForAnotherTenantsUserReturns404() {
        var response = getWithBearer("/api/v1/users/" + userB.getId() + "/permissions", tokenA);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assigningAnotherTenantsRoleReturns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenA);
        headers.set("Content-Type", "application/json");
        var response = restTemplate.exchange(
                "/api/v1/users/" + userA.getId() + "/roles",
                HttpMethod.POST,
                new HttpEntity<>("{\"roleId\":\"" + roleBId + "\"}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Tenant newTenant(String slugPrefix) {
        Tenant tenant = new Tenant(UUID.randomUUID(), slugPrefix + "-" + UUID.randomUUID(), slugPrefix);
        tenantRepository.saveAndFlush(tenant);
        return tenant;
    }

    private void grantAllReadWrite(UUID tenantId, UUID userId) {
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenantId, "full-" + roleId);
        for (String code : List.of("user:read", "user:write", "role:read", "role:write", "role:assign")) {
            UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);
    }

    private String login(String tenantSlug, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        String body = """
                {"tenantSlug":"%s","email":"%s","password":"%s"}
                """.formatted(tenantSlug, email, RAW_PASSWORD);
        String json = restTemplate.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), String.class).getBody();
        int start = json.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private org.springframework.http.ResponseEntity<String> getWithBearer(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
