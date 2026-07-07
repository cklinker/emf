-- ============================================================================
-- V82: Drop duplicate tenant tables from public schema
-- ============================================================================
-- V81 moved tbl_* tables to tenant schemas, but the worker bootstrap
-- recreated empty tables in public (because TenantContext was not set
-- during bootstrap). This migration drops those empty public copies.
--
-- Only drops tables that also exist in a tenant schema (i.e., duplicates).
-- ============================================================================

DO $$
DECLARE
    coll RECORD;
    tbl_name TEXT;
BEGIN
    FOR coll IN
        SELECT c.name, t.slug
        FROM collection c
        JOIN tenant t ON c.tenant_id = t.id
        WHERE c.system_collection = false
        AND c.active = true
        AND t.status != 'DECOMMISSIONED'
    LOOP
        tbl_name := 'tbl_' || coll.name;

        -- Only drop if the table exists in BOTH public and tenant schema
        IF EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = 'public' AND tablename = tbl_name
        ) AND EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = coll.slug AND tablename = tbl_name
        ) THEN
            EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', tbl_name);
            RAISE NOTICE 'Dropped duplicate public.%', tbl_name;
        END IF;
    END LOOP;
END $$;
