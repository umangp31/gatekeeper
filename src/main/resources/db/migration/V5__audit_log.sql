-- Append-only audit log (doc/scope.md §4, §12).

CREATE TABLE audit_log (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL REFERENCES tenants (id),
    actor_id   UUID,
    action     VARCHAR(64) NOT NULL,
    target     VARCHAR(256),
    payload    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Audit reads are always "latest first, per tenant" (§4.1).
CREATE INDEX idx_audit_tenant_time ON audit_log (tenant_id, created_at DESC);
