-- =============================================================================
-- V112: Add MANAGE_TENANTS system permission
-- =============================================================================
-- Adds the MANAGE_TENANTS permission to existing System Administrator profiles
-- so that only explicitly authorized users can create, update, or delete tenants.
-- New tenants provisioned at runtime will NOT receive this permission.

DO $$
DECLARE
    p RECORD;
BEGIN
    FOR p IN
        SELECT id, tenant_id
        FROM profile
        WHERE name = 'System Administrator' AND is_system = TRUE
    LOOP
        -- Skip if permission already exists for this profile
        IF NOT EXISTS (
            SELECT 1 FROM profile_system_permission
            WHERE profile_id = p.id AND permission_name = 'MANAGE_TENANTS'
        ) THEN
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p.id, 'MANAGE_TENANTS', TRUE);

            RAISE NOTICE 'Granted MANAGE_TENANTS to System Administrator profile % (tenant %)', p.id, p.tenant_id;
        END IF;
    END LOOP;
END $$;
