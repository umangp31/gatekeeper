package com.gatekeeper.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(description = "Refresh token received from login or the previous refresh")
        @NotBlank String refreshToken) {
}
