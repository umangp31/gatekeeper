package com.gatekeeper.common;

import com.gatekeeper.tenancy.TenantEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Base for every tenant-owned entity. The Hibernate filter is defined here but only takes
 * effect once enabled on the session per-request (see TenantFilterActivator, T-07) with the
 * current TenantContext value. Native queries bypass this filter entirely — see
 * doc/scope.md §5.2 for the rule that closes that gap.
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public abstract class TenantScopedEntity extends PersistableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
