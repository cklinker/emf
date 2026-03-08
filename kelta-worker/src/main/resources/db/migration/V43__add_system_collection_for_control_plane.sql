-- V43: Add system collection support and create control-plane authorization target
--
-- This migration:
-- 1. Adds a system_collection flag to the collection table
-- 2. Creates a system collection record for the control-plane API
-- 3. Creates an "Authenticated" policy granting access to any authenticated user
-- 4. Creates route_policy entries for all HTTP methods on the control-plane collection

-- 1. Add system_collection column to distinguish system collections from user collections
ALTER TABLE collection ADD COLUMN IF NOT EXISTS system_collection BOOLEAN DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_collection_system ON collection(system_collection);

-- 2. Insert control-plane system collection
-- Uses a well-known UUID so the gateway can reference it as a constant
INSERT INTO collection (id, tenant_id, name, display_name, description, path, storage_mode, active, current_version, system_collection, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000100',
    '00000000-0000-0000-0000-000000000001',
    '__control-plane',
    'Control Plane',
    'System collection representing the control plane API for authorization purposes',
    '/control',
    'VIRTUAL',
    true,
    1,
    true,
    NOW(),
    NOW()
) ON CONFLICT (tenant_id, name) DO NOTHING;

-- 3. Create "Authenticated" policy that grants access to any authenticated user
-- Administrators can later tighten this to specific roles via the Policies UI
INSERT INTO policy (id, tenant_id, name, description, expression, rules, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000200',
    '00000000-0000-0000-0000-000000000001',
    'authenticated',
    'Allows access to any authenticated user',
    'user.authenticated = true',
    '{"roles": ["*"]}',
    NOW()
) ON CONFLICT (tenant_id, name) DO NOTHING;

-- 4. Create route_policy entries for all HTTP methods on the control-plane collection
INSERT INTO route_policy (id, collection_id, operation, policy_id, created_at) VALUES
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000100', 'GET',    '00000000-0000-0000-0000-000000000200', NOW()),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000100', 'POST',   '00000000-0000-0000-0000-000000000200', NOW()),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000100', 'PUT',    '00000000-0000-0000-0000-000000000200', NOW()),
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000100', 'PATCH',  '00000000-0000-0000-0000-000000000200', NOW()),
    ('00000000-0000-0000-0000-000000000305', '00000000-0000-0000-0000-000000000100', 'DELETE', '00000000-0000-0000-0000-000000000200', NOW())
ON CONFLICT (collection_id, operation) DO NOTHING;
