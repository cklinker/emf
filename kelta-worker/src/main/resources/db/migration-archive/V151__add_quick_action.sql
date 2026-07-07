-- V151: quick_action — per-collection record/list quick actions (the QuickActionsMenu source).
-- Each row is a button definition (label + icon + action_type + type-specific config JSON) scoped
-- to a collection by name. CRUD via the generic dynamic collection path; useQuickActions fetches
-- the active actions for a collection. Mirrors the RLS pattern in V150 (record_share).

CREATE TABLE IF NOT EXISTS quick_action (
    id                    VARCHAR(36)  PRIMARY KEY,
    tenant_id             VARCHAR(36)  NOT NULL,
    collection_name       VARCHAR(200) NOT NULL,
    label                 VARCHAR(200) NOT NULL,
    icon                  VARCHAR(50),
    action_type           VARCHAR(30)  NOT NULL,
    context               VARCHAR(10)  NOT NULL DEFAULT 'record',
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    requires_confirmation BOOLEAN      NOT NULL DEFAULT FALSE,
    confirmation_message  VARCHAR(500),
    config                JSONB,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(36),
    updated_by            VARCHAR(36)
);

-- Hot lookup: active actions for a collection, in display order (the useQuickActions query).
CREATE INDEX IF NOT EXISTS idx_quick_action_lookup
    ON quick_action (tenant_id, collection_name, active, sort_order);

-- RLS: tenant isolation + admin bypass (empty tenant setting), matching record_share (V150).
ALTER TABLE quick_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE quick_action FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON quick_action;
DROP POLICY IF EXISTS admin_bypass     ON quick_action;
CREATE POLICY tenant_isolation ON quick_action
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON quick_action
    USING (current_setting('app.current_tenant_id', true) = '');
