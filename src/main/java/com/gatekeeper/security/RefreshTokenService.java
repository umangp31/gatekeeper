package com.gatekeeper.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens, stored only as a SHA-256 hash. Rotation: presenting a token revokes it
 * and issues a new one. Reuse of an already-revoked token is treated as theft and revokes the
 * user's entire chain — see doc/scope.md §9, §10 T4.
 */
@Service
public class RefreshTokenService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public String issue(UUID userId) {
        String rawToken = randomToken();
        RefreshToken entity = new RefreshToken(UUID.randomUUID(), userId, hash(rawToken), clock.instant().plus(REFRESH_TOKEN_TTL));
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Returns the userId the rotated token belongs to, and the raw value of its replacement.
     * noRollbackFor is essential here: on reuse, this method revokes the user's whole chain
     * and THEN throws to signal 401 — without it, @Transactional's default rollback-on-exception
     * would silently discard the revocation while still returning 401, defeating theft
     * detection (see doc/scope.md §10 T4).
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate(String presentedRawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(presentedRawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isRevoked()) {
            revokeAllForUser(existing.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (existing.getExpiresAt().isBefore(clock.instant())) {
            throw new InvalidRefreshTokenException();
        }

        existing.revoke();
        String newRawToken = issue(existing.getUserId());
        return new RotationResult(existing.getUserId(), newRawToken);
    }

    @Transactional
    public void revoke(String presentedRawToken) {
        refreshTokenRepository.findByTokenHash(hash(presentedRawToken)).ifPresent(RefreshToken::revoke);
    }

    private void revokeAllForUser(UUID userId) {
        refreshTokenRepository.findByUserIdAndRevokedFalse(userId).forEach(RefreshToken::revoke);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(UUID userId, String newRawRefreshToken) {
    }
}
