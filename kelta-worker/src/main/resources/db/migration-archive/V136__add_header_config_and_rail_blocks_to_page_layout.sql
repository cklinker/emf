-- V136: detail-page header config + rail blocks
--
-- Adds two JSONB columns to page_layout for storing the record-detail
-- design-handoff blocks:
--
--   header_config — RecordHeaderConfig (titleFields, avatarFrom, metaFields,
--                   actions). When NULL, the renderer auto-derives sensible
--                   defaults from common contact-ish fields on the record.
--
--   rail_blocks   — Ordered array of side-rail block configs (StatStrip,
--                   ScoreCard, TagsCard, MetadataCard, AICard, Timeline).
--                   Each item is { kind: "...", config: { ... } }. When
--                   NULL or empty, the renderer falls back to a single
--                   auto-derived system-info MetadataCard.
--
-- Both columns are nullable so existing layouts continue to render unchanged.

ALTER TABLE page_layout
    ADD COLUMN IF NOT EXISTS header_config JSONB,
    ADD COLUMN IF NOT EXISTS rail_blocks   JSONB;
