package com.gatekeeper.rbac;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Backs `@PreAuthorize("hasPermission('flag', 'write')")`. The permission code is
 * "<resource>:<action>" (see doc/scope.md §6.3). Effective permissions are resolved fresh via
 * the recursive CTE (PermissionQueryRepository, §6.2) for every check — Phase 4 (T-22) adds the
 * cache-aside layer in front of this without changing this class's contract.
 */
@Component
public class GatekeeperPermissionEvaluator implements PermissionEvaluator {

    private final CachedPermissionResolver cachedPermissionResolver;

    public GatekeeperPermissionEvaluator(CachedPermissionResolver cachedPermissionResolver) {
        this.cachedPermissionResolver = cachedPermissionResolver;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return hasPermission(authentication, null, String.valueOf(targetDomainObject), String.valueOf(permission));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        String resource = targetType;
        String action = String.valueOf(permission);
        return effectivePermissions(authentication).contains(resource + ":" + action);
    }

    public Set<String> effectivePermissions(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Set.of();
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant"));
        return cachedPermissionResolver.resolve(userId, tenantId);
    }
}
