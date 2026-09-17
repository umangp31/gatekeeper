package com.gatekeeper.common;

import org.springframework.http.HttpStatus;

/** Base for exceptions translated into RFC 7807 problem responses (see GlobalExceptionHandler, T-32). */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
