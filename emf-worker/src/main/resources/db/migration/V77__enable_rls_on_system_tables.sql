-- ============================================================================
-- V77: Enable Row Level Security (RLS) on tenant-scoped system tables
-- ============================================================================
-- Defense-in-depth: RLS policies enforce tenant isolation at the database level
-- for all shared system tables that have a tenant_id column.
--
-- The application sets `app.current_tenant_id` on each connection before queries.
-- RLS policies restrict row visibility to only the current tenant's data.
--
-- Tables WITHOUT tenant_id (child/junction tables) are NOT included here;
-- they inherit isolation through FK cascading from tenant-scoped parent tables.
-- ============================================================================

-- Helper function to avoid repetition
DO $$
DECLARE
    tbl TEXT;
    tables_with_tenant_id TEXT[] := ARRAY[
        'collection',
        'platform_user',
        'profile',
        'permission_set',
        'login_history',
        'security_audit_log',
        'setup_audit_trail',
        'user_group',
        'oidc_provider',
        'ui_page',
        'ui_menu',
        'package',
        'migration_run',
        'role',
        'policy',
        'global_picklist',
        'validation_rule',
        'record_type',
        'page_layout',
        'list_view',
        'report_folder',
        'report',
        'dashboard',
        'email_template',
        'email_log',
        'approval_process',
        'approval_instance',
        'flow',
        'flow_execution',
        'scheduled_job',
        'script',
        'webhook',
        'connected_app',
        'bulk_job',
        'org_wide_default',
        'sharing_rule',
        'record_share',
        'note',
        'file_attachment',
        'field_history',
        'field_type_config',
        'record_type_picklist',
        'workflow_action'
    ];
BEGIN
    FOREACH tbl IN ARRAY tables_with_tenant_id
    LOOP
        -- Check if the table exists before enabling RLS
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = tbl
        ) THEN
            -- Enable RLS on the table
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);

            -- Force RLS even for the table owner (important when app user = table owner)
            EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);

            -- Create tenant isolation policy
            -- current_setting('app.current_tenant_id', true) returns NULL if not set
            -- When NULL, no rows are visible (safe default)
            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant_id'', true))',
                tbl
            );

            -- Create a bypass policy for Flyway migrations and internal operations
            -- This policy allows full access when app.current_tenant_id is empty string
            -- (which is the connection-init default before a request sets the real tenant)
            EXECUTE format(
                'CREATE POLICY admin_bypass ON %I USING (current_setting(''app.current_tenant_id'', true) = '''')',
                tbl
            );

            RAISE NOTICE 'Enabled RLS on table: %', tbl;
        ELSE
            RAISE NOTICE 'Skipping RLS for non-existent table: %', tbl;
        END IF;
    END LOOP;
END $$;
