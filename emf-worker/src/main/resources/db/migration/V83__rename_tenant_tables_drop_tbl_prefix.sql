-- V83: Remove tbl_ prefix from user collection tables in tenant schemas.
--
-- Convention: user collection tables now use the collection name directly
-- (e.g., "products" instead of "tbl_products").
--
-- This migration renames tbl_<name> → <name> for all active non-system
-- collections in their respective tenant schemas.

DO $$
DECLARE
    coll RECORD;
    old_name TEXT;
    new_name TEXT;
BEGIN
    FOR coll IN
        SELECT c.name, t.slug
        FROM collection c
        JOIN tenant t ON c.tenant_id = t.id
        WHERE c.system_collection = false
          AND c.active = true
          AND t.status != 'DECOMMISSIONED'
    LOOP
        old_name := 'tbl_' || coll.name;
        new_name := coll.name;

        -- Only rename if the old name exists and new name does not
        IF EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = coll.slug AND tablename = old_name
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = coll.slug AND tablename = new_name
        ) THEN
            EXECUTE format('ALTER TABLE %I.%I RENAME TO %I', coll.slug, old_name, new_name);
            RAISE NOTICE 'Renamed %.% to %.%', coll.slug, old_name, coll.slug, new_name;
        END IF;
    END LOOP;
END $$;
