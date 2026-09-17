package com.gatekeeper.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Plain unit test for the ABAC attr()/sameTenant() predicates (doc/scope.md §6.4) — no Spring context needed. */
class AbacExpressionRootTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void attrReadsKnownAttributeFromJwt() {
        var root = rootWithAttrs(Map.of("department", "engineering"));
        assertThat(root.attr("department")).isEqualTo("engineering");
    }

    @Test
    void attrReturnsNullForUnknownKeyDenyByDefault() {
        var root = rootWithAttrs(Map.of("department", "engineering"));
        assertThat(root.attr("region")).isNull();
    }

    @Test
    void sameTenantMatchesCurrentTenantContext() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        var root = rootWithAttrs(Map.of());
        assertThat(root.sameTenant(tenantId)).isTrue();
        assertThat(root.sameTenant(UUID.randomUUID())).isFalse();
    }

    private GatekeeperMethodSecurityExpressionRoot rootWithAttrs(Map<String, Object> attrs) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("tenant", UUID.randomUUID().toString())
                .claim("attrs", attrs)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        return new GatekeeperMethodSecurityExpressionRoot(authentication, null);
    }
}
