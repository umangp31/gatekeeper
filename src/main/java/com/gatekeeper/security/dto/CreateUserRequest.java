package com.gatekeeper.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Schema(description = "Login email, unique within the tenant", example = "dev@acme.test")
        @NotBlank @Email String email,
        @Schema(description = "Plain-text password, at least 8 characters; stored BCrypt-hashed", example = "another-strong-pw")
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password) {
}
