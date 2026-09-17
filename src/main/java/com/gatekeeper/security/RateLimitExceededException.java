package com.gatekeeper.security;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
