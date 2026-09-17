package com.gatekeeper.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers the bootstrap path (doc/scope.md §11): {@code POST /tenants} provisions the tenant plus
 * its first admin (admin role with every permission + a user holding it), that admin can log in,
 * and the §9 user-role endpoints ({@code POST /users/{id}/roles},
 * {@code GET /users/{id}/permissions}) work end to end. Sandbox stand-in — runs against
 * docker-compose, see the T-05 note.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class LocalBootstrapAndRoleAssignmentIT {

    private static final String BOOTSTRAP_TOKEN = "local-dev-bootstrap-token";
    private static final String ADMIN_PASSWORD = "bootstrap-admin-pw";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

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
    void bootstrapProvisionsAdminWhoCanAssignRolesAndReadEffectivePermissions() {
        String slug = "boot-" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin@" + slug + ".test";

        // 1. Bootstrap the tenant + first admin.
        HttpHeaders bootstrap = json();
        bootstrap.set("X-Bootstrap-Token", BOOTSTRAP_TOKEN);
        String createBody = """
                {"slug":"%s","name":"Boot Co","adminEmail":"%s","adminPassword":"%s"}
                """.formatted(slug, adminEmail, ADMIN_PASSWORD);
        var created = restTemplate.postForEntity("/api/v1/tenants",
                new HttpEntity<>(createBody, bootstrap), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. The provisioned admin can log in.
        String token = login(slug, adminEmail);

        // 3. Admin creates a user and a role, then assigns the role.
        String memberEmail = "member@" + slug + ".test";
        UUID userId = UUID.fromString(extract(post("/api/v1/users",
                "{\"email\":\"%s\",\"password\":\"a-strong-pw\"}".formatted(memberEmail), token), "id"));
        UUID roleId = UUID.fromString(extract(post("/api/v1/roles",
                "{\"name\":\"flag-manager\"}", token), "id"));
        assertThat(post("/api/v1/roles/" + roleId + "/permissions/flag:write", null, token)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var assigned = post("/api/v1/users/" + userId + "/roles",
                "{\"roleId\":\"" + roleId + "\"}", token);
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 4. Effective permissions for that user now include the granted code.
        var perms = get("/api/v1/users/" + userId + "/permissions", token);
        assertThat(perms.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(perms.getBody()).contains("flag:write");

        // 5. Revoke and confirm it disappears.
        assertThat(restTemplate.exchange("/api/v1/users/" + userId + "/roles/" + roleId,
                HttpMethod.DELETE, new HttpEntity<>(bearer(token)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/users/" + userId + "/permissions", token).getBody())
                .doesNotContain("flag:write");
    }

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = json();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private org.springframework.http.ResponseEntity<String> post(String path, String body, String token) {
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), String.class);
    }

    private org.springframework.http.ResponseEntity<String> get(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    private String login(String slug, String email) {
        String body = """
                {"tenantSlug":"%s","email":"%s","password":"%s"}
                """.formatted(slug, email, ADMIN_PASSWORD);
        String responseBody = restTemplate.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(body, json()), String.class).getBody();
        return extract(org.springframework.http.ResponseEntity.ok(responseBody), "accessToken");
    }

    private String extract(org.springframework.http.ResponseEntity<String> response, String field) {
        String json = response.getBody();
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
