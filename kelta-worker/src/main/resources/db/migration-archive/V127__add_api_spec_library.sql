-- V127: OpenAPI Spec Library
-- Tenants upload OpenAPI 3.x specs (JSON or YAML) and the worker parses them
-- into a normalized operations index that the flow builder picker can search
-- across. The new CALL_API step (PR 4) executes operations chosen from this
-- catalog.

-- Required for trigram search across operation summaries / paths / tags.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE api_spec (
    id                VARCHAR(36)              PRIMARY KEY,
    tenant_id         VARCHAR(36)              NOT NULL REFERENCES tenant(id),
    name              VARCHAR(200)             NOT NULL,
    description       VARCHAR(1000),
    spec_version      VARCHAR(20)              NOT NULL,
    api_title         VARCHAR(500),
    api_version       VARCHAR(50),
    base_url          VARCHAR(1000),
    servers           JSONB,
    security_schemes  JSONB,
    source_type       VARCHAR(20)              NOT NULL,
    source_url        VARCHAR(2000),
    raw_spec          TEXT                     NOT NULL,
    raw_format        VARCHAR(10)              NOT NULL,
    parsed_spec       JSONB                    NOT NULL,
    spec_hash         VARCHAR(64)              NOT NULL,
    revision          INTEGER                  NOT NULL DEFAULT 1,
    is_active         BOOLEAN                  NOT NULL DEFAULT TRUE,
    last_imported_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(36),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(36),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_api_spec_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_api_spec_tenant      ON api_spec(tenant_id) WHERE is_active = TRUE;
CREATE INDEX idx_api_spec_tenant_all  ON api_spec(tenant_id);

COMMENT ON COLUMN api_spec.spec_version IS 'OpenAPI version (e.g. 3.0.3, 3.1.0)';
COMMENT ON COLUMN api_spec.source_type  IS 'INLINE_JSON | INLINE_YAML | URL';
COMMENT ON COLUMN api_spec.raw_format   IS 'json | yaml';
COMMENT ON COLUMN api_spec.spec_hash    IS 'sha-256 of raw_spec — used to detect re-imports';

CREATE TABLE api_operation (
    id                  VARCHAR(36)              PRIMARY KEY,
    tenant_id           VARCHAR(36)              NOT NULL,
    spec_id             VARCHAR(36)              NOT NULL
                            REFERENCES api_spec(id) ON DELETE CASCADE,
    operation_id        VARCHAR(200),
    synthetic_op_id     VARCHAR(200)             NOT NULL,
    http_method         VARCHAR(10)              NOT NULL,
    path_template       VARCHAR(1000)            NOT NULL,
    summary             VARCHAR(500),
    description         TEXT,
    tags                JSONB,
    parameters_schema   JSONB,
    request_body_schema JSONB,
    response_schemas    JSONB,
    security_required   JSONB,
    deprecated          BOOLEAN                  NOT NULL DEFAULT FALSE,
    search_text         TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_api_operation_spec_op UNIQUE (spec_id, synthetic_op_id)
);

CREATE INDEX idx_api_operation_tenant_method
    ON api_operation(tenant_id, http_method);
CREATE INDEX idx_api_operation_search
    ON api_operation USING gin (search_text gin_trgm_ops);
CREATE INDEX idx_api_operation_tags
    ON api_operation USING gin (tags jsonb_path_ops);
CREATE INDEX idx_api_operation_spec
    ON api_operation(spec_id);

COMMENT ON COLUMN api_operation.synthetic_op_id IS
    'Always present; synthesized as <METHOD>_<sanitized_path> when the spec omits operationId';
COMMENT ON COLUMN api_operation.search_text IS
    'Concatenation of method + path + summary + tags for trigram search';

-- Row-Level Security — mirror V77 / V126 pattern.
ALTER TABLE api_spec        ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_spec        FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON api_spec;
DROP POLICY IF EXISTS admin_bypass     ON api_spec;
CREATE POLICY tenant_isolation ON api_spec
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON api_spec
    USING (current_setting('app.current_tenant_id', true) = '');

ALTER TABLE api_operation   ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_operation   FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON api_operation;
DROP POLICY IF EXISTS admin_bypass     ON api_operation;
CREATE POLICY tenant_isolation ON api_operation
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON api_operation
    USING (current_setting('app.current_tenant_id', true) = '');

-- Permissions — same V126 pattern (NOT EXISTS guard, tenant_id required).
DO $$
DECLARE
    p RECORD;
BEGIN
    FOR p IN
        SELECT id, tenant_id
        FROM profile
        WHERE name = 'System Administrator' AND is_system = TRUE
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM profile_system_permission
            WHERE profile_id = p.id AND permission_name = 'MANAGE_API_SPECS'
        ) THEN
            INSERT INTO profile_system_permission
                (id, tenant_id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p.tenant_id, p.id,
                    'MANAGE_API_SPECS', TRUE);
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM profile_system_permission
            WHERE profile_id = p.id AND permission_name = 'VIEW_API_SPECS'
        ) THEN
            INSERT INTO profile_system_permission
                (id, tenant_id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p.tenant_id, p.id,
                    'VIEW_API_SPECS', TRUE);
        END IF;
    END LOOP;

    -- Standard users can browse specs (so they can pick one in a flow) but
    -- cannot upload or delete them.
    FOR p IN
        SELECT id, tenant_id
        FROM profile
        WHERE name = 'Standard User' AND is_system = TRUE
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM profile_system_permission
            WHERE profile_id = p.id AND permission_name = 'VIEW_API_SPECS'
        ) THEN
            INSERT INTO profile_system_permission
                (id, tenant_id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p.tenant_id, p.id,
                    'VIEW_API_SPECS', TRUE);
        END IF;
    END LOOP;
END $$;
