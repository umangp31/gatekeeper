package com.gatekeeper.security;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid tenant, email, or password");
    }
}
