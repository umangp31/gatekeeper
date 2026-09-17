package com.gatekeeper.flags.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateFlagRequest(
        @Schema(description = "Unique key of the flag within the tenant, used in URLs and evaluation calls", example = "new-checkout")
        @NotBlank String flagKey,
        @Schema(description = "Free-text description of what the flag controls", example = "Enables the redesigned checkout flow")
        String description) {
}
