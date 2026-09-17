package com.gatekeeper.tenancy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gatekeeper.tenancy.dto.CreateTenantRequest;
import com.gatekeeper.tenancy.dto.TenantResponse;
import com.gatekeeper.tenancy.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tenants", description = "Tenant bootstrap and management of the caller's own tenant.")
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private static final String BOOTSTRAP_TOKEN_HEADER = "X-Bootstrap-Token";

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Operation(summary = "Bootstrap a tenant", description = "Creates a new tenant plus its first admin user (the admin role holds every permission). Not JWT-protected: requires the shared secret in the X-Bootstrap-Token header instead. 403 on missing/wrong token, 409 if the slug is taken.")
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request,
                                                  @Parameter(description = "Shared bootstrap secret (BOOTSTRAP_TOKEN env var; local dev: local-dev-bootstrap-token)", required = true)
                                                  @RequestHeader(value = BOOTSTRAP_TOKEN_HEADER, required = false) String bootstrapToken) {
        Tenant tenant = tenantService.createTenant(request, bootstrapToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant));
    }

    @Operation(summary = "Get current tenant", description = "Returns the tenant the caller's access token belongs to.")
    @GetMapping("/me")
    public TenantResponse getCurrent() {
        return TenantResponse.from(tenantService.getCurrentTenant());
    }

    @Operation(summary = "Update current tenant", description = "Renames the caller's tenant. Requires the tenant:admin permission.")
    @PatchMapping("/me")
    public TenantResponse updateCurrent(@Valid @RequestBody UpdateTenantRequest request) {
        return TenantResponse.from(tenantService.updateCurrentTenant(request.name()));
    }
}
