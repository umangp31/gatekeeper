-- RBAC schema: roles, hierarchy, permissions (doc/scope.md §4, §4.1, §6).

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants (id),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_roles_tenant_name ON roles (tenant_id, name);

-- Child inherits the parent's permissions. The recursive CTE (§6.2) walks child -> parent,
-- so the leading column must be child_role_id; the reverse index supports impact analysis
-- (§8.3: "which users are affected when a role's permissions change").
CREATE TABLE role_hierarchy (
    parent_role_id UUID NOT NULL REFERENCES roles (id),
    child_role_id  UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (parent_role_id, child_role_id)
);

CREATE INDEX idx_hierarchy_child ON role_hierarchy (child_role_id, parent_role_id);
CREATE INDEX idx_hierarchy_parent ON role_hierarchy (parent_role_id, child_role_id);

-- Global catalogue, not tenant-scoped.
CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    CONSTRAINT uq_permissions_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id),
    permission_id UUID NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

-- Covering index: permission resolution becomes index-only (no heap fetch) - §4.1.
CREATE INDEX idx_role_perms_covering ON role_permissions (role_id) INCLUDE (permission_id);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id),
    role_id UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

-- User's direct roles, index-only - §4.1.
CREATE INDEX idx_user_roles_covering ON user_roles (user_id) INCLUDE (role_id);
