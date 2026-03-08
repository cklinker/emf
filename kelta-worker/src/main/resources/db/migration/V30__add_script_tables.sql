-- V30: Server-side scripting engine tables
-- Part of Phase 5 Stream A: Scripting Engine (5.1)

CREATE TABLE script (
    id                VARCHAR(36)   PRIMARY KEY,
    tenant_id         VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name              VARCHAR(200)  NOT NULL,
    description       VARCHAR(500),
    script_type       VARCHAR(30)   NOT NULL,
    language          VARCHAR(20)   DEFAULT 'javascript',
    source_code       TEXT          NOT NULL,
    active            BOOLEAN       DEFAULT true,
    version           INTEGER       DEFAULT 1,
    created_by        VARCHAR(36)   NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_script UNIQUE (tenant_id, name),
    CONSTRAINT chk_script_type CHECK (script_type IN (
        'BEFORE_TRIGGER','AFTER_TRIGGER','SCHEDULED','API_ENDPOINT',
        'VALIDATION','EVENT_HANDLER','EMAIL_HANDLER'
    ))
);

CREATE TABLE script_trigger (
    id               VARCHAR(36) PRIMARY KEY,
    script_id        VARCHAR(36) NOT NULL REFERENCES script(id) ON DELETE CASCADE,
    collection_id    VARCHAR(36) NOT NULL REFERENCES collection(id),
    trigger_event    VARCHAR(20) NOT NULL,
    execution_order  INTEGER     DEFAULT 0,
    active           BOOLEAN     DEFAULT true,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_script_trigger UNIQUE (script_id, collection_id, trigger_event),
    CONSTRAINT chk_trigger_event CHECK (trigger_event IN ('INSERT','UPDATE','DELETE'))
);

CREATE TABLE script_execution_log (
    id               VARCHAR(36) PRIMARY KEY,
    tenant_id        VARCHAR(36) NOT NULL,
    script_id        VARCHAR(36) NOT NULL REFERENCES script(id),
    status           VARCHAR(20) NOT NULL,
    trigger_type     VARCHAR(30),
    record_id        VARCHAR(36),
    duration_ms      INTEGER,
    cpu_ms           INTEGER,
    queries_executed INTEGER     DEFAULT 0,
    dml_rows         INTEGER     DEFAULT 0,
    callouts         INTEGER     DEFAULT 0,
    error_message    TEXT,
    log_output       TEXT,
    executed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exec_status CHECK (status IN ('SUCCESS','FAILURE','TIMEOUT','GOVERNOR_LIMIT'))
);

CREATE INDEX idx_script_tenant ON script(tenant_id);
CREATE INDEX idx_script_trigger_script ON script_trigger(script_id);
CREATE INDEX idx_script_trigger_collection ON script_trigger(collection_id, trigger_event);
CREATE INDEX idx_script_exec_log ON script_execution_log(tenant_id, script_id, executed_at DESC);
