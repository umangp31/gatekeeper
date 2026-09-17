package com.gatekeeper.tenancy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.gatekeeper.tenancy.Tenant;
import java.util.UUID;

public record TenantResponse(
        @Schema(description = "Tenant id (also the JWT tenant claim)")
        UUID id,
        @Schema(description = "URL-safe tenant identifier", example = "acme")
        String slug,
        @Schema(description = "Human-readable tenant name", example = "Acme Inc")
        String name,
        @Schema(description = "Lifecycle status of the tenant", example = "ACTIVE")
        Tenant.TenantStatus status) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.getStatus());
    }
}
