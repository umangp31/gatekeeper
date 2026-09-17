package com.gatekeeper.common;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Every entity in this project assigns its own UUID id in the constructor (never
 * {@code @GeneratedValue}), so Spring Data's default "is this new?" heuristic (id == null)
 * always says "no" and calls entityManager.merge() instead of persist(). merge() returns a
 * *different* managed instance, so JPA lifecycle callback mutations (e.g. TenantEntityListener
 * stamping tenant_id) never reach the object the caller is holding. Implementing Persistable
 * restores normal persist()-on-save behaviour for new entities.
 */
@MappedSuperclass
public abstract class PersistableEntity implements Persistable<UUID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
