package com.gatekeeper.rbac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.gatekeeper.rbac.Permission;
import java.util.UUID;

public record PermissionResponse(
        @Schema(description = "Permission id")
        UUID id,
        @Schema(description = "Permission code in resource:action form; this is what you grant to roles", example = "flag:write")
        String code,
        @Schema(description = "What the permission allows")
        String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getDescription());
    }
}
