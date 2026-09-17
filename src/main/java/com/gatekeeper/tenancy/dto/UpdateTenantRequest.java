package com.gatekeeper.tenancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateTenantRequest(
        @Schema(description = "New human-readable tenant name", example = "Acme Corporation")
        @NotBlank String name) {
}
