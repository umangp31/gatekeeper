package com.gatekeeper.tenancy;

import com.gatekeeper.common.PersistableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Not tenant-scoped itself — this is the entity tenant_id points at. See doc/scope.md §4. */
@Entity
@Table(name = "tenants")
public class Tenant extends PersistableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TenantStatus status = TenantStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tenant() {
    }

    public Tenant(UUID id, String slug, String name) {
        this.id = id;
        this.slug = slug;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum TenantStatus {
        ACTIVE, SUSPENDED
    }
}
