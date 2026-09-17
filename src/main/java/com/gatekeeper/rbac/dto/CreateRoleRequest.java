package com.gatekeeper.rbac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(
        @Schema(description = "Role name, unique within the tenant", example = "flag-manager")
        @NotBlank String name,
        @Schema(description = "Free-text description", example = "Can create and edit feature flags")
        String description) {
}
