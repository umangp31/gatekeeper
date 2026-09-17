package com.gatekeeper.rbac;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class RoleNotFoundException extends ApiException {

    public RoleNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Role not found");
    }
}
