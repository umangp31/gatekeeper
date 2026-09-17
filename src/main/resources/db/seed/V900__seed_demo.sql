-- Local-profile-only demo data (doc/scope.md §11). Never applied against prod — see
-- application-local.yml's spring.flyway.locations, which is the only profile that includes
-- this db/seed directory.

INSERT INTO tenants (id, slug, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'acme', 'Acme Inc');

-- viewer <- editor <- admin inheritance chain: editor inherits viewer's grants, admin
-- inherits editor's (and so viewer's, transitively).
INSERT INTO roles (id, tenant_id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'admin', 'Full access'),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'editor', 'Can manage flags and view roles'),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'viewer', 'Read-only');

-- Child inherits the parent's permissions (doc/scope.md §6.1): admin ⊇ editor ⊇ viewer.
INSERT INTO role_hierarchy (parent_role_id, child_role_id) VALUES
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000101'), -- admin  inherits editor
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000102'); -- editor inherits viewer

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000101', id FROM permissions; -- admin gets everything

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM permissions
WHERE code IN ('flag:read', 'flag:write', 'role:read', 'user:read');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000103', id FROM permissions
WHERE code IN ('flag:read', 'role:read', 'user:read');

-- Demo admin: admin@acme.test / admin123 (bcrypt(12), local dev only).
INSERT INTO users (id, tenant_id, email, password_hash, attributes) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000001',
     'admin@acme.test', '$2a$12$8Uls1PKZRzAY/zxJPBVUh.sHUfwQ.6d3khQn7jdmf7e9kEHqXARZi',
     '{"department":"engineering"}'::jsonb);

INSERT INTO user_roles (user_id, role_id) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101');

-- Two demo flags: one at 50% rollout, one whitelisted for the demo admin.
INSERT INTO feature_flags (id, tenant_id, flag_key, enabled, rollout_percentage, description) VALUES
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000001',
     'new-checkout', true, 50, 'Gradual rollout of the redesigned checkout flow'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000001',
     'beta-dashboard', false, 0, 'Early-access dashboard, whitelisted users only');

INSERT INTO flag_whitelist (flag_id, user_id) VALUES
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000201');
