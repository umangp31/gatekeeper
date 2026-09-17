package com.gatekeeper.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All time-dependent code (JWT iat/exp, token expiry, audit timestamps) must inject this
 * bean rather than calling Instant.now()/Clock.systemUTC() directly, so tests can fix time.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
