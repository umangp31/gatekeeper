package com.gatekeeper.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues and decodes access tokens. Always uses the injected Clock (never Instant.now()
 * directly) so tests can fix time to exercise expiry — see doc/scope.md §2, §10 (T3).
 */
@Service
public class TokenService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;

    public TokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
    }

    public Jwt issueAccessToken(UUID userId, UUID tenantId, String email, Map<String, Object> attributes) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gatekeeper")
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .subject(userId.toString())
                .claim("tenant", tenantId.toString())
                .claim("email", email)
                .claim("attrs", attributes)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims));
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }
}
