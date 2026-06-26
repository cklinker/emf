-- Governed AI agents: stored agent definitions (system prompt, allowed MCP tool
-- subset, model/token overrides, guardrails). Tenant-scoped like the other ai_* tables.

CREATE TABLE ai_agent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    system_prompt TEXT NOT NULL,
    model VARCHAR(255),                                 -- null → tenant/system default
    max_tokens INT,                                     -- null → tenant/system default
    allowed_tools JSONB NOT NULL DEFAULT '[]'::jsonb,   -- subset of MCP tool names; [] = no tools
    monthly_token_budget BIGINT,                        -- null → tenant quota only
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE INDEX idx_ai_agent_tenant ON ai_agent(tenant_id);

ALTER TABLE ai_agent ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_agent_tenant_isolation ON ai_agent
    USING (tenant_id = current_setting('app.current_tenant_id', true));
