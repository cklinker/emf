-- V84: Convert user collection columns from camelCase to snake_case.
--
-- Convention: all database columns use snake_case (e.g., first_name, email_address).
-- API field names remain camelCase; the application layer handles the mapping.
--
-- PostgreSQL folds unquoted identifiers to lowercase, so a column created as
-- "firstName" is actually stored as "firstname". This migration renames such
-- columns to their proper snake_case form (e.g., "firstname" → "first_name").
--
-- Also populates field.column_name with the snake_case equivalent for tracking.

DO $$
DECLARE
    rec RECORD;
    tbl_name TEXT;
    current_col TEXT;
    snake_col TEXT;
BEGIN
    FOR rec IN
        SELECT fld.id AS field_id,
               fld.name AS field_name,
               col.name AS collection_name,
               tnt.slug
        FROM field fld
        JOIN collection col ON fld.collection_id = col.id
        JOIN tenant tnt ON col.tenant_id = tnt.id
        WHERE col.system_collection = false
          AND col.active = true
          AND tnt.status != 'DECOMMISSIONED'
          AND fld.name ~ '[A-Z]'  -- Only fields with uppercase letters need conversion
    LOOP
        tbl_name := rec.collection_name;

        -- PostgreSQL folds unquoted identifiers to lowercase
        current_col := lower(rec.field_name);

        -- Convert camelCase to snake_case:
        -- 1. Insert _ between lowercase and uppercase: "firstName" → "first_Name"
        -- 2. Insert _ between consecutive uppercase and uppercase+lowercase: "XMLParser" → "XML_Parser"
        -- 3. Lowercase everything
        snake_col := lower(
            regexp_replace(
                regexp_replace(rec.field_name, '([a-z])([A-Z])', '\1_\2', 'g'),
                '([A-Z]+)([A-Z][a-z])', '\1_\2', 'g'
            )
        );

        -- Only rename if the names actually differ
        IF snake_col != current_col THEN
            -- Check column exists in the table
            IF EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = rec.slug
                  AND table_name = tbl_name
                  AND column_name = current_col
            ) THEN
                EXECUTE format('ALTER TABLE %I.%I RENAME COLUMN %I TO %I',
                    rec.slug, tbl_name, current_col, snake_col);
                RAISE NOTICE 'Renamed column %.%.% to %', rec.slug, tbl_name, current_col, snake_col;
            END IF;
        END IF;

        -- Populate field.column_name with the snake_case equivalent
        UPDATE field SET column_name = snake_col WHERE id = rec.field_id AND column_name IS NULL;
    END LOOP;
END $$;
