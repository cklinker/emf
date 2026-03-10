-- Flow versioning: track published versions of flow definitions
CREATE TABLE flow_version (
    id              VARCHAR(36) PRIMARY KEY,
    flow_id         VARCHAR(36) NOT NULL REFERENCES flow(id) ON DELETE CASCADE,
    version_number  INTEGER NOT NULL,
    definition      JSONB NOT NULL,
    change_summary  VARCHAR(500),
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_flow_version UNIQUE (flow_id, version_number)
);

CREATE INDEX idx_flow_version_flow ON flow_version(flow_id, version_number DESC);

-- Track which version each execution ran against
ALTER TABLE flow_execution ADD COLUMN IF NOT EXISTS flow_version INTEGER;

-- Track the currently published version on the flow itself
ALTER TABLE flow ADD COLUMN IF NOT EXISTS published_version INTEGER;
