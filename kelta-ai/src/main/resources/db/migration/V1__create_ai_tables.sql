-- Kelta AI Service Schema
-- All tables use tenant_id for RLS compatibility in the public schema

-- ============================================================================
-- Conversations
-- ============================================================================
CREATE TABLE ai_conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_conversation_tenant_user ON ai_conversation(tenant_id, user_id);

ALTER TABLE ai_conversation ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_conversation_tenant_isolation ON ai_conversation
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- ============================================================================
-- Messages
-- ============================================================================
CREATE TABLE ai_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    proposal_json JSONB,
    tokens_input INT NOT NULL DEFAULT 0,
    tokens_output INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_message_conversation ON ai_message(conversation_id);
CREATE INDEX idx_ai_message_tenant ON ai_message(tenant_id);

ALTER TABLE ai_message ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_message_tenant_isolation ON ai_message
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- ============================================================================
-- Token Usage (monthly aggregates per tenant)
-- ============================================================================
CREATE TABLE ai_token_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    year_month VARCHAR(7) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    request_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, year_month)
);

ALTER TABLE ai_token_usage ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_token_usage_tenant_isolation ON ai_token_usage
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- ============================================================================
-- AI Configuration (tenant-scoped, includes model selection)
-- ============================================================================
CREATE TABLE ai_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, config_key)
);

ALTER TABLE ai_config ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_config_tenant_isolation ON ai_config
    USING (tenant_id = current_setting('app.current_tenant_id', true));
