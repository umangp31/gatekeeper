package com.gatekeeper.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sandbox stand-in for §10 T1 (no token -> 401), T2 (malformed token -> 401), and T3 (expired
 * token -> 401, distinguished from malformed) — see T-05 note. Runs a real embedded server on
 * the `local` profile against docker-compose Postgres/Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class LocalAuthSecurityIT {

    private static final String RAW_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RSAPrivateKey jwtPrivateKey;

    private Tenant tenant;
    private User user;

    @BeforeEach
    void seedTenantAndUser() {
        tenant = new Tenant(UUID.randomUUID(), "auth-" + UUID.randomUUID(), "Auth Co");
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
        TenantContext.clear();
        tenantRepository.delete(tenant);
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        var response = login(tenant.getSlug(), user.getEmail(), RAW_PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("accessToken");
    }

    @Test
    void loginFailsWithWrongPassword() {
        var response = login(tenant.getSlug(), user.getEmail(), "wrong-password");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointRejectsMissingToken() {
        var response = restTemplate.getForEntity("/api/v1/tenants/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointRejectsMalformedToken() {
        var response = getWithBearer("/api/v1/tenants/me", "not-a-real-jwt");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("unauthorized");
    }

    @Test
    void protectedEndpointRejectsExpiredToken() throws Exception {
        String expiredToken = craftExpiredToken(user.getId());
        var response = getWithBearer("/api/v1/tenants/me", expiredToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("token_expired");
    }

    @Test
    void validTokenGrantsAccessToTenantMe() {
        String accessToken = extractAccessToken(login(tenant.getSlug(), user.getEmail(), RAW_PASSWORD).getBody());
        var response = getWithBearer("/api/v1/tenants/me", accessToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(tenant.getSlug());
    }

    private ResponseEntity<String> login(String tenantSlug, String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        String body = """
                {"tenantSlug":"%s","email":"%s","password":"%s"}
                """.formatted(tenantSlug, email, password);
        return restTemplate.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> getWithBearer(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(path, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String extractAccessToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private String craftExpiredToken(UUID userId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("gatekeeper")
                .subject(userId.toString())
                .claim("tenant", tenant.getId().toString())
                .issueTime(Date.from(now.minus(20, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(now.minus(5, ChronoUnit.MINUTES)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(jwtPrivateKey));
        return jwt.serialize();
    }
}
