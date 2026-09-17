-- Feature flags (doc/scope.md §4, §7).

CREATE TABLE feature_flags (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    flag_key            VARCHAR(128) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT false,
    rollout_percentage  INTEGER NOT NULL DEFAULT 0,
    description         VARCHAR(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_flags_tenant_key UNIQUE (tenant_id, flag_key),
    CONSTRAINT ck_flags_rollout_range CHECK (rollout_percentage BETWEEN 0 AND 100)
);

-- Bulk-evaluate (§9 GET /flags/evaluate) only ever needs enabled flags - keeps the index tiny.
CREATE INDEX idx_flags_tenant_enabled ON feature_flags (tenant_id) WHERE enabled = true;

CREATE TABLE flag_whitelist (
    flag_id UUID NOT NULL REFERENCES feature_flags (id),
    user_id UUID NOT NULL REFERENCES users (id),
    PRIMARY KEY (flag_id, user_id)
);

CREATE TABLE flag_environment_override (
    flag_id     UUID NOT NULL REFERENCES feature_flags (id),
    environment VARCHAR(32) NOT NULL,
    enabled     BOOLEAN NOT NULL,
    PRIMARY KEY (flag_id, environment)
);
