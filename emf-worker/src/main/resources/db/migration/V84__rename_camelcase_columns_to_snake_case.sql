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
    f RECORD;
    tbl_name TEXT;
    current_col TEXT;
    snake_col TEXT;
BEGIN
    FOR f IN
        SELECT f.id AS field_id,
               f.name AS field_name,
               c.name AS collection_name,
               t.slug
        FROM field f
        JOIN collection c ON f.collection_id = c.id
        JOIN tenant t ON c.tenant_id = t.id
        WHERE c.system_collection = false
          AND c.active = true
          AND t.status != 'DECOMMISSIONED'
          AND f.name ~ '[A-Z]'  -- Only fields with uppercase letters need conversion
    LOOP
        tbl_name := f.collection_name;

        -- PostgreSQL folds unquoted identifiers to lowercase
        current_col := lower(f.field_name);

        -- Convert camelCase to snake_case:
        -- 1. Insert _ between lowercase and uppercase: "firstName" → "first_Name"
        -- 2. Insert _ between consecutive uppercase and uppercase+lowercase: "XMLParser" → "XML_Parser"
        -- 3. Lowercase everything
        snake_col := lower(
            regexp_replace(
                regexp_replace(f.field_name, '([a-z])([A-Z])', '\1_\2', 'g'),
                '([A-Z]+)([A-Z][a-z])', '\1_\2', 'g'
            )
        );

        -- Only rename if the names actually differ
        IF snake_col != current_col THEN
            -- Check column exists in the table
            IF EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = f.slug
                  AND table_name = tbl_name
                  AND column_name = current_col
            ) THEN
                EXECUTE format('ALTER TABLE %I.%I RENAME COLUMN %I TO %I',
                    f.slug, tbl_name, current_col, snake_col);
                RAISE NOTICE 'Renamed column %.%.% to %', f.slug, tbl_name, current_col, snake_col;
            END IF;
        END IF;

        -- Populate field.column_name with the snake_case equivalent
        UPDATE field SET column_name = snake_col WHERE id = f.field_id AND column_name IS NULL;
    END LOOP;
END $$;
