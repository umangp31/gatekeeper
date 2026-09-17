package com.gatekeeper.tenancy;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidBootstrapTokenException extends ApiException {

    public InvalidBootstrapTokenException() {
        super(HttpStatus.FORBIDDEN, "Invalid or missing bootstrap token");
    }
}
