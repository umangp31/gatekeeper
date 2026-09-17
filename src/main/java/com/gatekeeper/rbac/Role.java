package com.gatekeeper.rbac;

import com.gatekeeper.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "roles")
public class Role extends TenantScopedEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Role() {
    }

    public Role(UUID id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
