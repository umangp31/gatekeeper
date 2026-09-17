package com.gatekeeper.tenancy;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when a tenant-scoped operation runs with no TenantContext set — never run unfiltered. */
public class TenantContextMissingException extends ApiException {

    public TenantContextMissingException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "No tenant context set for a tenant-scoped operation");
    }
}
