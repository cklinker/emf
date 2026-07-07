-- ============================================================================
-- V86: Enable Row Level Security (RLS) on child/junction tables
-- ============================================================================
-- Now that these tables have tenant_id (V85), enable RLS policies following
-- the same pattern as V77 for the parent tables.
-- ============================================================================

DO $$
DECLARE
    tbl TEXT;
    child_tables TEXT[] := ARRAY[
        'picklist_value',
        'picklist_dependency',
        'layout_section',
        'layout_field',
        'layout_related_list',
        'dashboard_component',
        'profile_system_permission',
        'profile_object_permission',
        'profile_field_permission',
        'permset_system_permission',
        'permset_object_permission',
        'permset_field_permission',
        'group_membership',
        'group_permission_set',
        'ui_menu_item',
        'tenant_module_action'
    ];
BEGIN
    FOREACH tbl IN ARRAY child_tables
    LOOP
        -- Verify the table exists and has tenant_id before enabling RLS
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = tbl AND column_name = 'tenant_id'
        ) THEN
            -- Enable RLS on the table
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);

            -- Force RLS even for the table owner
            EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);

            -- Drop policies if they already exist (idempotent)
            EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl);
            EXECUTE format('DROP POLICY IF EXISTS admin_bypass ON %I', tbl);

            -- Tenant isolation policy: rows visible only to current tenant
            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant_id'', true))',
                tbl
            );

            -- Admin bypass: full access when app.current_tenant_id is empty string
            EXECUTE format(
                'CREATE POLICY admin_bypass ON %I USING (current_setting(''app.current_tenant_id'', true) = '''')',
                tbl
            );

            RAISE NOTICE 'Enabled RLS on table: %', tbl;
        ELSE
            RAISE NOTICE 'Skipping table % (not found or no tenant_id column)', tbl;
        END IF;
    END LOOP;
END $$;
