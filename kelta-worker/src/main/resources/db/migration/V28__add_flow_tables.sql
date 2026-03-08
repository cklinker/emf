-- V28: Flow engine and flow execution tables
-- Part of Phase 4 Stream D: Flow Engine (4.3)

CREATE TABLE flow (
    id              VARCHAR(36)   PRIMARY KEY,
    tenant_id       VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    flow_type       VARCHAR(30)   NOT NULL,
    active          BOOLEAN       DEFAULT false,
    version         INTEGER       DEFAULT 1,
    trigger_config  JSONB,
    definition      JSONB         NOT NULL,
    created_by      VARCHAR(36)   NOT NULL REFERENCES platform_user(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_flow UNIQUE (tenant_id, name),
    CONSTRAINT chk_flow_type CHECK (flow_type IN (
        'RECORD_TRIGGERED','SCHEDULED','AUTOLAUNCHED','SCREEN'
    ))
);

CREATE TABLE flow_execution (
    id                VARCHAR(36)   PRIMARY KEY,
    tenant_id         VARCHAR(36)   NOT NULL,
    flow_id           VARCHAR(36)   NOT NULL REFERENCES flow(id),
    status            VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
    started_by        VARCHAR(36),
    trigger_record_id VARCHAR(36),
    variables         JSONB         DEFAULT '{}',
    current_node_id   VARCHAR(100),
    error_message     TEXT,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_flow_status CHECK (status IN ('RUNNING','COMPLETED','FAILED','WAITING','CANCELLED'))
);

CREATE INDEX idx_flow_tenant ON flow(tenant_id);
CREATE INDEX idx_flow_exec_status ON flow_execution(tenant_id, status);
CREATE INDEX idx_flow_exec_flow ON flow_execution(flow_id);
