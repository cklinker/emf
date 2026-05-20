-- Add content_blocks JSONB column to ai_message to hold the full Anthropic
-- content-block sequence (text + tool_use + tool_result) for a single turn.
-- Backfill: convert legacy content + proposal_json into a content_blocks array.
-- Old proposals become "tool_use_legacy" blocks that buildMessageHistory will
-- skip when replaying history to Claude (no matching tool_result would break
-- Anthropic validation).

ALTER TABLE ai_message
    ADD COLUMN content_blocks JSONB;

UPDATE ai_message
SET content_blocks = (
    CASE
        WHEN content IS NOT NULL AND content <> '' THEN
            jsonb_build_array(jsonb_build_object('type', 'text', 'text', content))
        ELSE '[]'::jsonb
    END
) || (
    CASE
        WHEN proposal_json IS NOT NULL THEN
            jsonb_build_array(jsonb_build_object(
                'type', 'tool_use_legacy',
                'proposal', proposal_json::jsonb,
                'proposalId', proposal_json::jsonb ->> 'id'
            ))
        ELSE '[]'::jsonb
    END
)
WHERE content_blocks IS NULL;

ALTER TABLE ai_message ALTER COLUMN content_blocks SET DEFAULT '[]'::jsonb;
ALTER TABLE ai_message ALTER COLUMN content_blocks SET NOT NULL;

CREATE INDEX idx_ai_message_content_blocks_gin ON ai_message USING gin (content_blocks);
