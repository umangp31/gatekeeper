package com.gatekeeper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * GatekeeperMethodSecurityExpressionHandler is a @Component implementing
 * MethodSecurityExpressionHandler, so Spring Security's method-security auto-configuration
 * picks it up automatically once @EnableMethodSecurity is active — no extra bean wiring needed.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
