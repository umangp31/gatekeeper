package com.gatekeeper.flags;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

/** Sandbox stand-in for §9 evaluate endpoints, see T-05 note. Exercises the real HTTP stack. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class LocalFlagEvaluationControllerIT {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private JdbcTemplate jdbc;

    private Tenant tenant;
    private com.gatekeeper.security.User user;
    private String accessToken;

    @BeforeEach
    void seed() {
        tenant = new Tenant(UUID.randomUUID(), "flageval-" + UUID.randomUUID(), "FlagEval Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());
        user = userService.createUserInternal("u-" + UUID.randomUUID() + "@test", RAW_PASSWORD);

        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenant.getId(), "flag-admin");
        for (String code : List.of("flag:read", "flag:write")) {
            UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", user.getId(), roleId);

        // Authenticate once (service-level, for flag setup) and once via real HTTP login for the endpoint calls.
        authenticateContext(user.getId(), tenant.getId());
        featureFlagService.createFlag("enabled-flag", null);
        featureFlagService.updateFlag("enabled-flag", true, 100, null, 0L);
        featureFlagService.createFlag("disabled-flag", null);
        featureFlagService.createFlag("whitelisted-flag", null);
        featureFlagService.addToWhitelist("whitelisted-flag", user.getId());
        SecurityContextHolder.clearContext();

        accessToken = login(tenant.getSlug(), user.getEmail());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM flag_whitelist");
        jdbc.update("DELETE FROM flag_environment_override");
        jdbc.update("DELETE FROM feature_flags");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        TenantContext.set(tenant.getId());
        jdbc.update("DELETE FROM users");
        tenantRepository.delete(tenant);
        TenantContext.clear();
    }

    @Test
    void bulkEvaluateReturnsCorrectMapForAllFlags() {
        var response = getWithBearer("/api/v1/flags/evaluate", accessToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"enabled-flag\":true")
                .contains("\"disabled-flag\":false")
                .contains("\"whitelisted-flag\":true");
    }

    @Test
    void singleEvaluateForSelfWorks() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        var response = restTemplate.exchange("/api/v1/flags/enabled-flag/evaluate", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"enabled\":true");
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

    private void authenticateContext(UUID userId, UUID tenantId) {
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
