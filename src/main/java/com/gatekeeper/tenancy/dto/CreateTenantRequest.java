package com.gatekeeper.tenancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Bootstrap payload for {@code POST /tenants} (doc/scope.md §9, §11). Besides the tenant
 * itself it provisions the tenant's first administrator: an {@code admin} role holding every
 * catalogue permission, and a user with that role — the only way a freshly bootstrapped
 * tenant gets a principal that can log in and call the rest of the API.
 */
public record CreateTenantRequest(
        @Schema(description = "URL-safe identifier, lowercase alphanumeric and hyphens, 2-64 chars; used as tenantSlug at login", example = "acme")
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{2,64}$", message = "slug must be lowercase alphanumeric with hyphens") String slug,
        @Schema(description = "Human-readable tenant name", example = "Acme Inc")
        @NotBlank String name,
        @Schema(description = "Email of the first admin user to provision", example = "admin@acme.test")
        @NotBlank @Email String adminEmail,
        @Schema(description = "Password for the first admin, at least 8 characters", example = "change-me-please")
        @NotBlank @Size(min = 8, message = "admin password must be at least 8 characters") String adminPassword) {
}
