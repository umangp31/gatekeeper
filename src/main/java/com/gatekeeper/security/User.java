package com.gatekeeper.security;

import com.gatekeeper.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tenant-scoped: see doc/scope.md §4, §5. attributes backs the ABAC layer (§6.4). */
@Entity
@Table(name = "users")
public class User extends TenantScopedEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> attributes = new HashMap<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
