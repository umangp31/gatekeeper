package com.gatekeeper.tenancy;

import com.gatekeeper.common.TenantScopedEntity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import java.util.Objects;
import java.util.UUID;

/**
 * Stamps tenant_id on insert and rejects writes whose tenant_id disagrees with the current
 * TenantContext. See doc/scope.md §5.1.
 */
public class TenantEntityListener {

    @PrePersist
    public void prePersist(TenantScopedEntity entity) {
        UUID tenantId = TenantContext.require();
        if (entity.getTenantId() == null) {
            entity.setTenantId(tenantId);
        } else if (!Objects.equals(entity.getTenantId(), tenantId)) {
            throw new CrossTenantAccessException();
        }
    }

    @PreUpdate
    @PreRemove
    public void preUpdateOrRemove(TenantScopedEntity entity) {
        UUID tenantId = TenantContext.require();
        if (!Objects.equals(entity.getTenantId(), tenantId)) {
            throw new CrossTenantAccessException();
        }
    }
}
