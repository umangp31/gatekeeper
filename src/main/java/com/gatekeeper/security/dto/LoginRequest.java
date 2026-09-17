package com.gatekeeper.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "Slug of the tenant the user belongs to", example = "acme")
        @NotBlank String tenantSlug,
        @Schema(description = "User's login email", example = "admin@acme.test")
        @NotBlank String email,
        @Schema(description = "User's password", example = "admin123")
        @NotBlank String password) {
}
