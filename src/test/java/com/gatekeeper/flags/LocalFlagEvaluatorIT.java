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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

/** Sandbox stand-in for §10 T15 (flag evaluation order), see T-05 note. Runs against the `local` profile, so environment = "local". */
@SpringBootTest
@ActiveProfiles("local")
class LocalFlagEvaluatorIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private FlagEvaluator flagEvaluator;

    @Autowired
    private JdbcTemplate jdbc;

    private Tenant tenant;
    private UUID userId;

    @BeforeEach
    void seed() {
        tenant = new Tenant(UUID.randomUUID(), "eval-" + UUID.randomUUID(), "Eval Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());
        userId = userService.createUserInternal("u-" + UUID.randomUUID() + "@test", "pw").getId();

        UUID callerId = userService.createUserInternal("caller-" + UUID.randomUUID() + "@test", "pw").getId();
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, name) VALUES (?,?,?)", roleId, tenant.getId(), "flag-admin");
        for (String code : List.of("flag:read", "flag:write")) {
            UUID permissionId = jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?,?)", roleId, permissionId);
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", callerId, roleId);
        authenticateAs(callerId, tenant.getId());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log");
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM flag_whitelist");
        jdbc.update("DELETE FROM flag_environment_override");
        jdbc.update("DELETE FROM feature_flags");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("DELETE FROM users");
        TenantContext.set(tenant.getId());
        tenantRepository.delete(tenant);
        TenantContext.clear();
    }

    @Test
    void unknownFlagIsFalse() {
        assertThat(flagEvaluator.evaluate("does-not-exist", userId, tenant.getId())).isFalse();
    }

    @Test
    void environmentOverrideOutranksEverything() {
        featureFlagService.createFlag("override-flag", null);
        featureFlagService.updateFlag("override-flag", true, 100, null, 0L); // enabled, 100% - would be true anyway
        featureFlagService.addToWhitelist("override-flag", userId);
        featureFlagService.setEnvironmentOverride("override-flag", "local", false);

        assertThat(flagEvaluator.evaluate("override-flag", userId, tenant.getId()))
                .as("override says false even though enabled+100%+whitelisted would all say true")
                .isFalse();
    }

    @Test
    void whitelistOutranksGlobalDisable() {
        featureFlagService.createFlag("wl-flag", null); // enabled=false by default
        featureFlagService.addToWhitelist("wl-flag", userId);

        assertThat(flagEvaluator.evaluate("wl-flag", userId, tenant.getId()))
                .as("whitelisted user gets true even though the flag is globally disabled")
                .isTrue();
    }

    @Test
    void globallyDisabledFlagIsFalseForNonWhitelistedUser() {
        featureFlagService.createFlag("disabled-flag", null);

        assertThat(flagEvaluator.evaluate("disabled-flag", userId, tenant.getId())).isFalse();
    }

    @Test
    void rolloutZeroPercentIsFalseWhenEnabled() {
        featureFlagService.createFlag("zero-rollout", null);
        featureFlagService.updateFlag("zero-rollout", true, 0, null, 0L);

        assertThat(flagEvaluator.evaluate("zero-rollout", userId, tenant.getId())).isFalse();
    }

    @Test
    void rolloutHundredPercentIsTrueWhenEnabled() {
        featureFlagService.createFlag("full-rollout", null);
        featureFlagService.updateFlag("full-rollout", true, 100, null, 0L);

        assertThat(flagEvaluator.evaluate("full-rollout", userId, tenant.getId())).isTrue();
    }

    private void authenticateAs(UUID id, UUID tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(id.toString())
                .claim("tenant", tenantId.toString())
                .claim("attrs", Map.of())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
