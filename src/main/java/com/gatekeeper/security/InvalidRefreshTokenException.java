package com.gatekeeper.security;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid, expired, or revoked refresh token");
    }
}
