-- Change tenant_id from BIGINT to VARCHAR(255) to support UUID tenant IDs
-- Must drop RLS policies first since they reference tenant_id

-- Drop existing policies
DROP POLICY IF EXISTS ai_conversation_tenant_isolation ON ai_conversation;
DROP POLICY IF EXISTS ai_message_tenant_isolation ON ai_message;
DROP POLICY IF EXISTS ai_token_usage_tenant_isolation ON ai_token_usage;
DROP POLICY IF EXISTS ai_config_tenant_isolation ON ai_config;

-- Alter column types
ALTER TABLE ai_conversation ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;
ALTER TABLE ai_message ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;
ALTER TABLE ai_token_usage ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;
ALTER TABLE ai_config ALTER COLUMN tenant_id TYPE VARCHAR(255) USING tenant_id::text;

-- Recreate policies
CREATE POLICY ai_conversation_tenant_isolation ON ai_conversation
    USING (tenant_id = current_setting('app.current_tenant_id', true));

CREATE POLICY ai_message_tenant_isolation ON ai_message
    USING (tenant_id = current_setting('app.current_tenant_id', true));

CREATE POLICY ai_token_usage_tenant_isolation ON ai_token_usage
    USING (tenant_id = current_setting('app.current_tenant_id', true));

CREATE POLICY ai_config_tenant_isolation ON ai_config
    USING (tenant_id = current_setting('app.current_tenant_id', true));
