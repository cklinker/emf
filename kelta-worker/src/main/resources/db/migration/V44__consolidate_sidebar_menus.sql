-- V44: Consolidate sidebar menus into domain-grouped sections
--
-- Replaces the single flat "Main Navigation" menu (34 items) with 5 domain-grouped
-- menus: Data Model, Security & Access, UI Builder, Automation, Platform.

-- 1. Add display_order column for explicit menu ordering
ALTER TABLE ui_menu ADD COLUMN IF NOT EXISTS display_order INTEGER DEFAULT 0;

-- 2. Remove existing flat menu structure
DELETE FROM ui_menu_item WHERE menu_id = '00000000-0000-0000-0000-000000000001';
DELETE FROM ui_menu WHERE id = '00000000-0000-0000-0000-000000000001';

-- 3. Create domain-grouped menus

-- Menu 1: Data Model
INSERT INTO ui_menu (id, tenant_id, name, description, display_order, created_at, updated_at)
VALUES ('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-000000000001',
        'Data Model', 'Data model management', 1, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at) VALUES
    ('00000000-0000-0001-0001-000000000001', '00000000-0000-0000-0001-000000000001', 'Collections', '/collections', 'collections', 1, true, NOW()),
    ('00000000-0000-0001-0001-000000000002', '00000000-0000-0000-0001-000000000001', 'Picklists', '/picklists', 'picklist', 2, true, NOW()),
    ('00000000-0000-0001-0001-000000000003', '00000000-0000-0000-0001-000000000001', 'Resources', '/resources', 'resources', 3, true, NOW());

-- Menu 2: Security & Access
INSERT INTO ui_menu (id, tenant_id, name, description, display_order, created_at, updated_at)
VALUES ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-000000000001',
        'Security & Access', 'Security and access control', 2, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at) VALUES
    ('00000000-0000-0001-0002-000000000001', '00000000-0000-0000-0001-000000000002', 'Users', '/users', 'users', 1, true, NOW()),
    ('00000000-0000-0001-0002-000000000002', '00000000-0000-0000-0001-000000000002', 'Roles', '/roles', 'roles', 2, true, NOW()),
    ('00000000-0000-0001-0002-000000000003', '00000000-0000-0000-0001-000000000002', 'Policies', '/policies', 'policy', 3, true, NOW()),
    ('00000000-0000-0001-0002-000000000004', '00000000-0000-0000-0001-000000000002', 'Profiles', '/profiles', 'user', 4, true, NOW()),
    ('00000000-0000-0001-0002-000000000005', '00000000-0000-0000-0001-000000000002', 'Permission Sets', '/permission-sets', 'security', 5, true, NOW()),
    ('00000000-0000-0001-0002-000000000006', '00000000-0000-0000-0001-000000000002', 'Sharing', '/sharing', 'sharing', 6, true, NOW()),
    ('00000000-0000-0001-0002-000000000007', '00000000-0000-0000-0001-000000000002', 'Role Hierarchy', '/role-hierarchy', 'roles', 7, true, NOW()),
    ('00000000-0000-0001-0002-000000000008', '00000000-0000-0000-0001-000000000002', 'OIDC Providers', '/oidc-providers', 'oidc', 8, true, NOW());

-- Menu 3: UI Builder
INSERT INTO ui_menu (id, tenant_id, name, description, display_order, created_at, updated_at)
VALUES ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0000-000000000001',
        'UI Builder', 'UI configuration tools', 3, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at) VALUES
    ('00000000-0000-0001-0003-000000000001', '00000000-0000-0000-0001-000000000003', 'Page Layouts', '/layouts', 'pages', 1, true, NOW()),
    ('00000000-0000-0001-0003-000000000002', '00000000-0000-0000-0001-000000000003', 'List Views', '/listviews', 'browser', 2, true, NOW()),
    ('00000000-0000-0001-0003-000000000003', '00000000-0000-0000-0001-000000000003', 'Pages', '/pages', 'pages', 3, true, NOW()),
    ('00000000-0000-0001-0003-000000000004', '00000000-0000-0000-0001-000000000003', 'Menus', '/menus', 'menus', 4, true, NOW());

-- Menu 4: Automation
INSERT INTO ui_menu (id, tenant_id, name, description, display_order, created_at, updated_at)
VALUES ('00000000-0000-0000-0001-000000000004', '00000000-0000-0000-0000-000000000001',
        'Automation', 'Workflow and automation tools', 4, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at) VALUES
    ('00000000-0000-0001-0004-000000000001', '00000000-0000-0000-0001-000000000004', 'Workflow Rules', '/workflow-rules', 'workflow', 1, true, NOW()),
    ('00000000-0000-0001-0004-000000000002', '00000000-0000-0000-0001-000000000004', 'Approvals', '/approvals', 'approval', 2, true, NOW()),
    ('00000000-0000-0001-0004-000000000003', '00000000-0000-0000-0001-000000000004', 'Flows', '/flows', 'flow', 3, true, NOW()),
    ('00000000-0000-0001-0004-000000000004', '00000000-0000-0000-0001-000000000004', 'Scheduled Jobs', '/scheduled-jobs', 'schedule', 4, true, NOW()),
    ('00000000-0000-0001-0004-000000000005', '00000000-0000-0000-0001-000000000004', 'Scripts', '/scripts', 'script', 5, true, NOW());

-- Menu 5: Platform
INSERT INTO ui_menu (id, tenant_id, name, description, display_order, created_at, updated_at)
VALUES ('00000000-0000-0000-0001-000000000005', '00000000-0000-0000-0000-000000000001',
        'Platform', 'Platform administration', 5, NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at) VALUES
    ('00000000-0000-0001-0005-000000000001', '00000000-0000-0000-0001-000000000005', 'System Health', '/system-health', 'health', 1, true, NOW()),
    ('00000000-0000-0001-0005-000000000002', '00000000-0000-0000-0001-000000000005', 'Workers', '/workers', 'workers', 2, true, NOW()),
    ('00000000-0000-0001-0005-000000000003', '00000000-0000-0000-0001-000000000005', 'Tenants', '/tenants', 'tenants', 3, true, NOW()),
    ('00000000-0000-0001-0005-000000000004', '00000000-0000-0000-0001-000000000005', 'Tenant Dashboard', '/tenant-dashboard', 'dashboard', 4, true, NOW()),
    ('00000000-0000-0001-0005-000000000005', '00000000-0000-0000-0001-000000000005', 'Audit Trail', '/audit-trail', 'audit', 5, true, NOW()),
    ('00000000-0000-0001-0005-000000000006', '00000000-0000-0000-0001-000000000005', 'Governor Limits', '/governor-limits', 'limits', 6, true, NOW()),
    ('00000000-0000-0001-0005-000000000007', '00000000-0000-0000-0001-000000000005', 'Plugins', '/plugins', 'plugin', 7, true, NOW()),
    ('00000000-0000-0001-0005-000000000008', '00000000-0000-0000-0001-000000000005', 'Webhooks', '/webhooks', 'webhook', 8, true, NOW()),
    ('00000000-0000-0001-0005-000000000009', '00000000-0000-0000-0001-000000000005', 'Connected Apps', '/connected-apps', 'apps', 9, true, NOW()),
    ('00000000-0000-0001-0005-000000000010', '00000000-0000-0000-0001-000000000005', 'Email Templates', '/email-templates', 'email', 10, true, NOW()),
    ('00000000-0000-0001-0005-000000000011', '00000000-0000-0000-0001-000000000005', 'Packages', '/packages', 'packages', 11, true, NOW()),
    ('00000000-0000-0001-0005-000000000012', '00000000-0000-0000-0001-000000000005', 'Migrations', '/migrations', 'migration', 12, true, NOW()),
    ('00000000-0000-0001-0005-000000000013', '00000000-0000-0000-0001-000000000005', 'Bulk Jobs', '/bulk-jobs', 'bulk', 13, true, NOW());
