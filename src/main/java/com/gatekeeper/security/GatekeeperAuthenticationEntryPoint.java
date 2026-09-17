package com.gatekeeper.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns 401 for every authentication failure, but distinguishes an expired token from a
 * malformed/invalid one in the response body so clients (and §10 T2/T3) can tell them apart.
 * T-32 will fold this into the RFC 7807 problem-details format.
 */
@Component
public class GatekeeperAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String message = authException.getMessage() == null ? "" : authException.getMessage().toLowerCase();
        String reason = message.contains("expired") ? "token_expired" : "unauthorized";

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + reason + "\"}");
    }
}
