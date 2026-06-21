-- Durable audit log of governed agent runs: one row per execution (or refusal), capturing the
-- input, outcome status, tool-call trace, token usage and final text. No FK to ai_agent so the
-- audit trail survives an agent being deleted.

CREATE TABLE ai_agent_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    agent_id UUID NOT NULL,
    user_id VARCHAR(255),
    input TEXT,
    status VARCHAR(40) NOT NULL,                        -- completed | max_iterations | budget_exceeded | refused_disabled | refused_token_limit | error
    tool_calls JSONB NOT NULL DEFAULT '[]'::jsonb,      -- ordered AgentToolTrace list
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    iterations INT NOT NULL DEFAULT 0,
    final_text TEXT,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_agent_execution_agent ON ai_agent_execution(tenant_id, agent_id, created_at DESC);

ALTER TABLE ai_agent_execution ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_agent_execution_tenant_isolation ON ai_agent_execution
    USING (tenant_id = current_setting('app.current_tenant_id', true));
