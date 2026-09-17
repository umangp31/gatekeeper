package com.gatekeeper.rbac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.gatekeeper.rbac.Role;
import java.util.UUID;

public record RoleResponse(
        @Schema(description = "Role id; use in /users/{id}/roles and /roles/{id}/parents/{parentId}")
        UUID id,
        @Schema(description = "Role name", example = "flag-manager")
        String name,
        @Schema(description = "Free-text description")
        String description) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription());
    }
}
