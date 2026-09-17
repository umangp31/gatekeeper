package com.gatekeeper.rbac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record UpdateRoleRequest(
        @Schema(description = "New description", example = "Can create and edit feature flags")
        String description) {
}
