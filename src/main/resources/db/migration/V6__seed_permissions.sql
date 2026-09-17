-- Repeatable migration: keeps the global permission catalogue in sync (doc/scope.md §6.3, §9).

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'tenant:admin', 'Manage tenant settings'),
    (gen_random_uuid(), 'user:read', 'Read users within the tenant'),
    (gen_random_uuid(), 'user:write', 'Create/update users within the tenant'),
    (gen_random_uuid(), 'role:read', 'Read roles and the permission catalogue'),
    (gen_random_uuid(), 'role:write', 'Create/update roles and role-hierarchy edges'),
    (gen_random_uuid(), 'role:assign', 'Assign/revoke roles on users'),
    (gen_random_uuid(), 'flag:read', 'Read and evaluate feature flags'),
    (gen_random_uuid(), 'flag:write', 'Create/update feature flags, whitelists, overrides')
ON CONFLICT (code) DO NOTHING;
