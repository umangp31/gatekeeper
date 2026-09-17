package com.gatekeeper.flags;

import com.gatekeeper.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** @Version backs optimistic locking on concurrent PUT /flags/{key} (doc/scope.md §7.3, §9). */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag extends TenantScopedEntity {

    @Id
    private UUID id;

    @Column(name = "flag_key", nullable = false, length = 128)
    private String flagKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage;

    @Column(length = 512)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FeatureFlag() {
    }

    public FeatureFlag(UUID id, String flagKey, String description) {
        this.id = id;
        this.flagKey = flagKey;
        this.description = description;
        this.enabled = false;
        this.rolloutPercentage = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
        this.rolloutPercentage = rolloutPercentage;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getVersion() {
        return version;
    }
}
