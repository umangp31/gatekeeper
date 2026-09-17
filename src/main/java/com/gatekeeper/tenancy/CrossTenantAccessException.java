package com.gatekeeper.tenancy;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an entity's tenant_id does not match the current TenantContext on write.
 * Mapped to 404 (not 403) by the global exception handler (T-32) — a 403 would confirm the
 * resource exists in another tenant. See doc/scope.md §5.1, §9.
 */
public class CrossTenantAccessException extends ApiException {

    public CrossTenantAccessException() {
        super(HttpStatus.NOT_FOUND, "Resource not found");
    }
}
