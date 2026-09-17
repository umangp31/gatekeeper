package com.gatekeeper.flags.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateFlagRequest(
        @Schema(description = "New global on/off state (omit to leave unchanged)", example = "true")
        Boolean enabled,
        @Schema(description = "New rollout percentage, 0-100 (omit to leave unchanged)", example = "25")
        Integer rolloutPercentage,
        @Schema(description = "New description (omit to leave unchanged)")
        String description,
        @Schema(description = "Version from the last GET; a stale value is rejected with 409", example = "3")
        @NotNull Long version) {
}
