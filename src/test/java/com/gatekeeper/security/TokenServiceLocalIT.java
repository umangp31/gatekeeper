package com.gatekeeper.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class TokenServiceLocalIT {

    @Autowired
    private TokenService tokenService;

    @Test
    void issuedTokenDecodesWithAllClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Jwt issued = tokenService.issueAccessToken(userId, tenantId, "user@test", Map.of("department", "eng"));
        Jwt decoded = tokenService.decode(issued.getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("tenant")).isEqualTo(tenantId.toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("user@test");
        assertThat(decoded.getClaimAsMap("attrs")).containsEntry("department", "eng");
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    }
}
