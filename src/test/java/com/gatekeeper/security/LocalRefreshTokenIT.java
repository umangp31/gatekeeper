package com.gatekeeper.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §10 T4 (refresh-token rotation + revocation), see T-05 note.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class LocalRefreshTokenIT {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Tenant tenant;
    private User user;

    @BeforeEach
    void seed() {
        tenant = new Tenant(UUID.randomUUID(), "refresh-" + UUID.randomUUID(), "Refresh Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());
        user = userService.createUserInternal("u-" + UUID.randomUUID() + "@test", RAW_PASSWORD);
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        refreshTokenRepository.deleteAll(refreshTokenRepository.findByUserId(user.getId()));
        TenantContext.set(tenant.getId());
        userRepository.deleteAll(userRepository.findAll());
        tenantRepository.delete(tenant);
        TenantContext.clear();
    }

    @Test
    void refreshRotatesTokenAndReuseOfOldOneRevokesWholeChain() {
        String firstRefreshToken = extractRefreshToken(login());

        // Normal rotation: use the token once, get a new one back.
        var rotateResponse = refresh(firstRefreshToken);
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secondRefreshToken = extractRefreshToken(rotateResponse.getBody());
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // Reusing the now-revoked first token is treated as theft -> 401.
        var reuseResponse = refresh(firstRefreshToken);
        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The whole chain (including the second, legitimately-issued token) is now dead.
        var secondTokenNowDeadResponse = refresh(secondRefreshToken);
        assertThat(secondTokenNowDeadResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheToken() {
        String refreshToken = extractRefreshToken(login());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
        var logoutResponse = restTemplate.postForEntity("/api/v1/auth/logout", new HttpEntity<>(body, headers), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var reuseAfterLogout = refresh(refreshToken);
        assertThat(reuseAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        String body = """
                {"tenantSlug":"%s","email":"%s","password":"%s"}
                """.formatted(tenant.getSlug(), user.getEmail(), RAW_PASSWORD);
        return restTemplate.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), String.class).getBody();
    }

    private ResponseEntity<String> refresh(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
        return restTemplate.postForEntity("/api/v1/auth/refresh", new HttpEntity<>(body, headers), String.class);
    }

    private String extractRefreshToken(String json) {
        int start = json.indexOf("\"refreshToken\":\"") + "\"refreshToken\":\"".length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
