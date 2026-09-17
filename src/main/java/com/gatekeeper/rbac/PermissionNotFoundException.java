package com.gatekeeper.rbac;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class PermissionNotFoundException extends ApiException {

    public PermissionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Permission not found");
    }
}
