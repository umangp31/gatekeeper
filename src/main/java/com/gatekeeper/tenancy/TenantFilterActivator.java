package com.gatekeeper.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Enables the Hibernate "tenantFilter" (see TenantScopedEntity) on the current session at the
 * start of every transactional method, using whatever TenantContext holds at that point. If no
 * tenant context is set, the filter is left disabled — callers that must not run unfiltered are
 * responsible for calling TenantContext.require() themselves (e.g. TenantEntityListener, T-08).
 */
@Aspect
@Component
public class TenantFilterActivator {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* org.springframework.data.repository.Repository+.*(..))")
    public void enableTenantFilterIfPresent() {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        if (session.getEnabledFilter("tenantFilter") == null) {
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
    }
}
