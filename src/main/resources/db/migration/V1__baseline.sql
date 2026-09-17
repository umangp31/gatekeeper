-- Baseline: tenants + users (doc/scope.md §4)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       VARCHAR(64) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants (id),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    attributes    JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled       BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tenant-scoped lookup: tenant predicate (injected by the Hibernate filter, §5) and the
-- email lookup are satisfied by a single index seek because tenant_id leads (§4.1).
CREATE UNIQUE INDEX idx_users_tenant_email ON users (tenant_id, email);
