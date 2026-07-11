-- V169 created start_time/end_time as TIME while their field definitions in
-- SystemCollectionDefinitions are STRING(8). The generic JSON:API write path
-- binds STRING fields as varchar, so every POST /api/telehealth-availability
-- failed with "column is of type time without time zone but expression is of
-- type character varying" — even though the slice-4 spec designates the
-- generic admin JSON:API as the management path for availability rows.
-- Align the physical columns with the declared field type; values keep the
-- HH24:MI:SS shape, which SlotService parses and which orders identically.
ALTER TABLE telehealth_availability
    ALTER COLUMN start_time TYPE varchar(8) USING to_char(start_time, 'HH24:MI:SS'),
    ALTER COLUMN end_time   TYPE varchar(8) USING to_char(end_time, 'HH24:MI:SS');
