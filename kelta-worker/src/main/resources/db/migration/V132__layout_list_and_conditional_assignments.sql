-- V132: LIST-type page-layout defaults + conditional layout assignments
--
-- Adds default filter/sort/limit columns to page_layout so LIST-type layouts
-- can ship admin-defined defaults. Adds condition + evaluation_order to
-- layout_assignment so the resolver can pick a layout based on record values
-- with an explicit, user-controllable ordering.

ALTER TABLE page_layout
    ADD COLUMN IF NOT EXISTS default_filter         JSONB,
    ADD COLUMN IF NOT EXISTS default_sort_field     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS default_sort_direction VARCHAR(4)  NOT NULL DEFAULT 'ASC',
    ADD COLUMN IF NOT EXISTS default_row_limit      INTEGER     NOT NULL DEFAULT 50;

ALTER TABLE layout_assignment
    ADD COLUMN IF NOT EXISTS condition        JSONB,
    ADD COLUMN IF NOT EXISTS evaluation_order INTEGER NOT NULL DEFAULT 100;

-- Backfill evaluation_order for existing unconditional rows so insertion order
-- (created_at) is preserved through the new resolver. Multiplying by 10 leaves
-- gaps for users to insert new rules between existing ones.
UPDATE layout_assignment
   SET evaluation_order = sub.rn * 10
  FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY collection_id ORDER BY created_at) AS rn
          FROM layout_assignment) sub
 WHERE layout_assignment.id = sub.id
   AND layout_assignment.condition IS NULL;

CREATE INDEX IF NOT EXISTS idx_layout_assignment_resolve
    ON layout_assignment(collection_id, evaluation_order);
