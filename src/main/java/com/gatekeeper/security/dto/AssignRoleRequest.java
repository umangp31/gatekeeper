package com.gatekeeper.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignRoleRequest(
        @Schema(description = "Id of the role to assign (must belong to the same tenant)")
        @NotNull UUID roleId) {
}
