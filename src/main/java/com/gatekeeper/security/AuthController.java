package com.gatekeeper.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gatekeeper.security.dto.LoginRequest;
import com.gatekeeper.security.dto.LoginResponse;
import com.gatekeeper.security.dto.RefreshRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirements // public endpoints: no bearer token
@Tag(name = "Auth", description = "Login, refresh-token rotation and logout. No bearer token required.")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Operation(summary = "Log in", description = "Authenticates a user of the given tenant with email + password. Returns a 15-minute RS256 access token and an opaque refresh token. Rate-limited to 10 attempts per 60s per tenant+email+IP (429 with Retry-After). 401 on bad credentials.")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        loginRateLimiter.checkAllowed(request.tenantSlug(), request.email(), clientIp(servletRequest));
        return authService.login(request.tenantSlug(), request.email(), request.password());
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor != null ? forwardedFor.split(",")[0].trim() : request.getRemoteAddr();
        // Normalize IPv6 loopback forms to the IPv4 form so local dev/tests get one consistent
        // rate-limit bucket regardless of which stack the JVM/OS picked for "localhost".
        return "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip;
    }

    @Operation(summary = "Refresh tokens", description = "Exchanges a valid refresh token for a new access + refresh token pair. The old refresh token is invalidated (rotation). Reusing an already-rotated token is treated as theft and revokes the entire token chain (401).")
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Log out", description = "Revokes the given refresh token so it can no longer be used to mint access tokens. Already-issued access tokens stay valid until they expire (max 15 min). Always returns 204.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
