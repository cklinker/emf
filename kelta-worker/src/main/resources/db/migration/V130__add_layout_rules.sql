-- V130: Per-layout client-side rules engine
-- Adds layout_rule table for compute/validate/default/transform rules attached to page_layout.
-- Also extends validation_rule with enforce_on_client + severity so existing collection-wide
-- validations can opt into client-side mirroring.

CREATE TABLE IF NOT EXISTS layout_rule (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    layout_id       VARCHAR(36)  NOT NULL REFERENCES page_layout(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    kind            VARCHAR(20)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT true,
    when_events     JSONB        NOT NULL,
    target_field    VARCHAR(100),
    depends_on      JSONB,
    body            JSONB        NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_layout_rule_name UNIQUE (tenant_id, layout_id, name),
    CONSTRAINT chk_layout_rule_kind CHECK (kind IN ('COMPUTE','VALIDATE','DEFAULT','TRANSFORM'))
);

CREATE INDEX IF NOT EXISTS idx_layout_rule_layout ON layout_rule(layout_id, active, sort_order);
CREATE INDEX IF NOT EXISTS idx_layout_rule_tenant ON layout_rule(tenant_id);

-- Enable RLS for tenant isolation (pattern from V77)
ALTER TABLE layout_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE layout_rule FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON layout_rule;
DROP POLICY IF EXISTS admin_bypass ON layout_rule;
CREATE POLICY tenant_isolation ON layout_rule
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON layout_rule
    USING (current_setting('app.current_tenant_id', true) = '');

-- Extend validation_rule so server-side rules can opt into client-side enforcement
ALTER TABLE validation_rule
    ADD COLUMN IF NOT EXISTS enforce_on_client BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS severity VARCHAR(10) NOT NULL DEFAULT 'ERROR';

ALTER TABLE validation_rule
    DROP CONSTRAINT IF EXISTS chk_validation_rule_severity;
ALTER TABLE validation_rule
    ADD CONSTRAINT chk_validation_rule_severity CHECK (severity IN ('ERROR','WARNING'));
