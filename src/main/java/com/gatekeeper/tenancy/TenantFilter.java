package com.gatekeeper.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates TenantContext for the duration of one request, from the "tenant" claim of the
 * authenticated JWT. The X-Tenant-Slug header is honoured ONLY on /api/v1/auth/**, where no
 * token exists yet (login itself). Everywhere else the token is the sole authority — see
 * doc/scope.md §5.1. Must run after the JWT auth filter (wired in SecurityConfig, T-12) and
 * always clears the context in `finally`.
 */
@Component
@Order(100)
public class TenantFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String TENANT_SLUG_HEADER = "X-Tenant-Slug";

    private final TenantRepository tenantRepository;

    public TenantFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            resolveTenantId(request).ifPresent(TenantContext::set);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private java.util.Optional<UUID> resolveTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantIdClaim = jwt.getClaimAsString("tenant");
            if (tenantIdClaim != null) {
                return java.util.Optional.of(UUID.fromString(tenantIdClaim));
            }
        }

        if (request.getRequestURI().startsWith(AUTH_PATH_PREFIX)) {
            String slug = request.getHeader(TENANT_SLUG_HEADER);
            if (slug != null) {
                return tenantRepository.findBySlug(slug).map(Tenant::getId);
            }
        }

        return java.util.Optional.empty();
    }
}
