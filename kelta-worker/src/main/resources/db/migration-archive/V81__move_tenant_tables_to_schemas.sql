-- ============================================================================
-- V81: Move tenant collection tables back to tenant schemas
-- ============================================================================
-- V80 restored tbl_* tables to the public schema as a temporary fix while
-- schema-per-tenant was behind a feature flag. Now that schema-per-tenant is
-- always on, this migration moves the tables back to their tenant schemas.
--
-- Uses ALTER TABLE ... SET SCHEMA which atomically moves the table, its
-- indexes, constraints, and owned sequences.
-- ============================================================================

DO $$
DECLARE
    coll RECORD;
    tbl_name TEXT;
    table_exists BOOLEAN;
BEGIN
    FOR coll IN
        SELECT c.name, c.tenant_id, t.slug
        FROM collection c
        JOIN tenant t ON c.tenant_id = t.id
        WHERE c.system_collection = false
        AND c.active = true
        AND t.status != 'DECOMMISSIONED'
    LOOP
        tbl_name := 'tbl_' || coll.name;

        -- Check if the table exists in the public schema
        SELECT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public'
            AND table_name = tbl_name
        ) INTO table_exists;

        IF table_exists THEN
            -- Ensure the target schema exists
            EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', coll.slug);

            -- Move the table to the tenant schema
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA %I', tbl_name, coll.slug);
            RAISE NOTICE 'Moved table % to schema %', tbl_name, coll.slug;
        ELSE
            RAISE NOTICE 'Table % does not exist in public schema, skipping', tbl_name;
        END IF;
    END LOOP;
END $$;
