package com.gatekeeper.rbac;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class GatekeeperMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    private final GatekeeperPermissionEvaluator gatekeeperPermissionEvaluator;

    public GatekeeperMethodSecurityExpressionHandler(GatekeeperPermissionEvaluator gatekeeperPermissionEvaluator) {
        this.gatekeeperPermissionEvaluator = gatekeeperPermissionEvaluator;
        setPermissionEvaluator(gatekeeperPermissionEvaluator);
    }

    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(Authentication authentication,
                                                                                MethodInvocation invocation) {
        GatekeeperMethodSecurityExpressionRoot root =
                new GatekeeperMethodSecurityExpressionRoot(authentication, gatekeeperPermissionEvaluator);
        root.setPermissionEvaluator(gatekeeperPermissionEvaluator);
        root.setTrustResolver(getTrustResolver());
        root.setRoleHierarchy(getRoleHierarchy());
        return root;
    }
}
