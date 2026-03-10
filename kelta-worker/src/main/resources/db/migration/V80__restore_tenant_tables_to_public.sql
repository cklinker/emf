-- ============================================================================
-- V80: Restore tenant collection tables to public schema
-- ============================================================================
-- V79 moved tbl_* tables from public to tenant schemas, but the worker
-- bootstrap process recreated empty tables in public. This migration:
--   1. Copies data from tenant schema tables back to the public tables
--   2. Drops the now-redundant tenant schema copies
--
-- This restores correct behavior while schema-per-tenant is disabled.
-- When schema-per-tenant is later enabled, tenant tables will be
-- recreated in tenant schemas by the application's initializeCollection().
-- ============================================================================

DO $$
DECLARE
    tenant_slug TEXT;
    tbl_name TEXT;
    full_table TEXT;
    col_list TEXT;
BEGIN
    -- For each non-decommissioned tenant
    FOR tenant_slug IN
        SELECT slug FROM tenant WHERE status != 'DECOMMISSIONED' AND slug IS NOT NULL
    LOOP
        -- For each tbl_* table in the tenant's schema
        FOR tbl_name IN
            SELECT tablename FROM pg_tables
            WHERE schemaname = tenant_slug AND tablename LIKE 'tbl_%'
        LOOP
            -- Check if the corresponding public table exists
            IF EXISTS (
                SELECT 1 FROM pg_tables
                WHERE schemaname = 'public' AND tablename = tbl_name
            ) THEN
                -- Get the column names that exist in BOTH the tenant and public tables
                SELECT string_agg(quote_ident(c.column_name), ', ')
                INTO col_list
                FROM information_schema.columns c
                WHERE c.table_schema = tenant_slug
                  AND c.table_name = tbl_name
                  AND EXISTS (
                      SELECT 1 FROM information_schema.columns p
                      WHERE p.table_schema = 'public'
                        AND p.table_name = tbl_name
                        AND p.column_name = c.column_name
                  );

                IF col_list IS NOT NULL THEN
                    -- Copy data from tenant schema to public (skip duplicates)
                    EXECUTE format(
                        'INSERT INTO public.%I (%s) SELECT %s FROM %I.%I ON CONFLICT (id) DO NOTHING',
                        tbl_name, col_list, col_list, tenant_slug, tbl_name
                    );
                    RAISE NOTICE 'Copied data from %.% to public.%', tenant_slug, tbl_name, tbl_name;
                END IF;
            END IF;

            -- Drop the tenant schema copy
            EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE', tenant_slug, tbl_name);
            RAISE NOTICE 'Dropped %.%', tenant_slug, tbl_name;
        END LOOP;
    END LOOP;
END $$;
