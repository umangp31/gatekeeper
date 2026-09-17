package com.gatekeeper.audit;

import com.gatekeeper.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only — nothing ever updates or deletes a row here. See doc/scope.md §4, §12. */
@Entity
@Table(name = "audit_log")
public class AuditLog extends TenantScopedEntity {

    @Id
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 256)
    private String target;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UUID id, UUID actorId, String action, String target, Map<String, Object> payload) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.target = target;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
