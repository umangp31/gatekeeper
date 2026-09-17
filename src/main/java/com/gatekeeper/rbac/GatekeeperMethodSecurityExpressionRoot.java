package com.gatekeeper.rbac;

import com.gatekeeper.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Adds hasPermission(code), sameTenant(id), and attr(name) to @PreAuthorize expressions. See
 * doc/scope.md §6.3, §6.4.
 */
public class GatekeeperMethodSecurityExpressionRoot extends SecurityExpressionRoot
        implements MethodSecurityExpressionOperations {

    private final GatekeeperPermissionEvaluator gatekeeperPermissionEvaluator;
    private Object filterObject;
    private Object returnObject;

    public GatekeeperMethodSecurityExpressionRoot(Authentication authentication,
                                                    GatekeeperPermissionEvaluator gatekeeperPermissionEvaluator) {
        super(authentication);
        this.gatekeeperPermissionEvaluator = gatekeeperPermissionEvaluator;
    }

    /** Shorthand for hasPermission(code, "") style checks where the code is already "resource:action". */
    public boolean hasPermission(String code) {
        return gatekeeperPermissionEvaluator.effectivePermissions(getAuthentication()).contains(code);
    }

    /** True if the given tenant id is the caller's own tenant (from the JWT `tenant` claim). */
    public boolean sameTenant(UUID tenantId) {
        UUID current = TenantContext.get();
        return current != null && current.equals(tenantId);
    }

    /** Reads a value from the caller's JWT `attrs` claim (ABAC, §6.4). Unknown key -> null (deny-by-default). */
    @SuppressWarnings("unchecked")
    public Object attr(String name) {
        if (!(getAuthentication().getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        Map<String, Object> attrs = jwt.getClaimAsMap("attrs");
        return attrs == null ? null : attrs.get(name);
    }

    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    @Override
    public Object getFilterObject() {
        return filterObject;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    @Override
    public Object getReturnObject() {
        return returnObject;
    }

    @Override
    public Object getThis() {
        return this;
    }
}
