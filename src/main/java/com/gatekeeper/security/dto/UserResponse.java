package com.gatekeeper.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.gatekeeper.security.User;
import java.util.Map;
import java.util.UUID;

public record UserResponse(
        @Schema(description = "User id (also the JWT sub claim)")
        UUID id,
        @Schema(description = "Login email", example = "dev@acme.test")
        String email,
        @Schema(description = "Whether the user may log in", example = "true")
        boolean enabled,
        @Schema(description = "ABAC attributes attached to the user")
        Map<String, Object> attributes) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.isEnabled(), user.getAttributes());
    }
}
