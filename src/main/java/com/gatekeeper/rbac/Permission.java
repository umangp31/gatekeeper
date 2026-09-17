package com.gatekeeper.rbac;

import com.gatekeeper.common.PersistableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Global catalogue, not tenant-scoped. Seeded by R__seed_permissions.sql (T-14). */
@Entity
@Table(name = "permissions")
public class Permission extends PersistableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    @Column(length = 512)
    private String description;

    protected Permission() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
