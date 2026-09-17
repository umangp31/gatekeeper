package com.gatekeeper.flags.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record EvaluateResponse(
        @Schema(description = "Key of the evaluated flag", example = "new-checkout")
        String flagKey,
        @Schema(description = "Whether the flag is ON for the evaluated user", example = "true")
        boolean enabled) {
}
