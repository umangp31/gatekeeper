package com.gatekeeper.security;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "User not found");
    }
}
