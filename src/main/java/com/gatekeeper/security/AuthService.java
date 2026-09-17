package com.gatekeeper.security;

import com.gatekeeper.security.dto.LoginResponse;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(TenantRepository tenantRepository, UserRepository userRepository,
                        PasswordEncoder passwordEncoder, TokenService tokenService,
                        RefreshTokenService refreshTokenService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * The login body carries tenantSlug directly (see doc/scope.md §9), so tenant resolution
     * happens here rather than depending on TenantFilter's X-Tenant-Slug header path.
     */
    @Transactional
    public LoginResponse login(String tenantSlug, String email, String rawPassword) {
        UUID tenantId = tenantRepository.findBySlug(tenantSlug)
                .map(com.gatekeeper.tenancy.Tenant::getId)
                .orElseThrow(InvalidCredentialsException::new);
        TenantContext.set(tenantId);
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        Jwt jwt = tokenService.issueAccessToken(user.getId(), tenantId, user.getEmail(), user.getAttributes());
        String refreshToken = refreshTokenService.issue(user.getId());
        long expiresIn = TokenService.ACCESS_TOKEN_TTL.getSeconds();
        return new LoginResponse(jwt.getTokenValue(), refreshToken, "Bearer", expiresIn);
    }

    /**
     * Rotates the refresh token and issues a fresh access token. The new access token needs the
     * user's current tenant + email + attributes, so this re-reads the user directly by id
     * (refresh tokens aren't tenant-scoped — see doc/scope.md §4 — so no TenantContext is
     * required or set here).
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResponse refresh(String presentedRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(presentedRefreshToken);
        User user = userRepository.findById(rotation.userId()).orElseThrow(InvalidRefreshTokenException::new);
        Jwt jwt = tokenService.issueAccessToken(user.getId(), user.getTenantId(), user.getEmail(), user.getAttributes());
        long expiresIn = TokenService.ACCESS_TOKEN_TTL.getSeconds();
        return new LoginResponse(jwt.getTokenValue(), rotation.newRawRefreshToken(), "Bearer", expiresIn);
    }

    @Transactional
    public void logout(String presentedRefreshToken) {
        refreshTokenService.revoke(presentedRefreshToken);
    }
}
