package com.gatekeeper.flags.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record EnvironmentOverrideRequest(
        @Schema(description = "Value the flag is pinned to in this environment, overriding every other rule", example = "true")
        @NotNull Boolean enabled) {
}
