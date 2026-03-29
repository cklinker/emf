-- Change tenant_id from BIGINT to VARCHAR(255) to support UUID tenant IDs

ALTER TABLE ai_conversation ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;
ALTER TABLE ai_message ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;
ALTER TABLE ai_token_usage ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;
ALTER TABLE ai_config ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;
