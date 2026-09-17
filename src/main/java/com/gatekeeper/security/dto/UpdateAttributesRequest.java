package com.gatekeeper.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record UpdateAttributesRequest(
        @Schema(description = "Free-form JSON attributes used by ABAC rules via attr('name'); replaces the existing set", example = "{\"department\":\"finance\",\"region\":\"eu\"}")
        @NotNull Map<String, Object> attributes) {
}
