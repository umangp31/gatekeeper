package com.gatekeeper.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record LoginResponse(
        @Schema(description = "RS256 JWT to send as 'Authorization: Bearer <token>'; carries sub (user id), tenant and attrs claims")
        String accessToken,
        @Schema(description = "Opaque single-use token for POST /auth/refresh; rotated on every use")
        String refreshToken,
        @Schema(description = "Always 'Bearer'", example = "Bearer")
        String tokenType,
        @Schema(description = "Access-token lifetime in seconds", example = "900")
        long expiresInSeconds) {
}
