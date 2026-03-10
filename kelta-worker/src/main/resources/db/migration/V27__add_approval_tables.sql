-- V27: Approval process, steps, instances, and step instances
-- Part of Phase 4 Stream C: Approval Processes (4.2)

CREATE TABLE approval_process (
    id                       VARCHAR(36)   PRIMARY KEY,
    tenant_id                VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    collection_id            VARCHAR(36)   NOT NULL REFERENCES collection(id),
    name                     VARCHAR(200)  NOT NULL,
    description              VARCHAR(1000),
    active                   BOOLEAN       DEFAULT true,
    entry_criteria           TEXT,
    record_editability       VARCHAR(20)   DEFAULT 'LOCKED',
    initial_submitter_field  VARCHAR(100),
    on_submit_field_updates  JSONB         DEFAULT '[]',
    on_approval_field_updates JSONB        DEFAULT '[]',
    on_rejection_field_updates JSONB       DEFAULT '[]',
    on_recall_field_updates  JSONB         DEFAULT '[]',
    allow_recall             BOOLEAN       DEFAULT true,
    execution_order          INTEGER       DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_approval_process UNIQUE (tenant_id, collection_id, name)
);

CREATE TABLE approval_step (
    id                        VARCHAR(36)  PRIMARY KEY,
    approval_process_id       VARCHAR(36)  NOT NULL REFERENCES approval_process(id) ON DELETE CASCADE,
    step_number               INTEGER      NOT NULL,
    name                      VARCHAR(200) NOT NULL,
    description               VARCHAR(500),
    entry_criteria            TEXT,
    approver_type             VARCHAR(30)  NOT NULL,
    approver_id               VARCHAR(36),
    approver_field            VARCHAR(100),
    unanimity_required        BOOLEAN      DEFAULT false,
    escalation_timeout_hours  INTEGER,
    escalation_action         VARCHAR(20),
    on_approve_action         VARCHAR(20)  DEFAULT 'NEXT_STEP',
    on_reject_action          VARCHAR(20)  DEFAULT 'REJECT_FINAL',
    CONSTRAINT uq_approval_step UNIQUE (approval_process_id, step_number)
);

CREATE TABLE approval_instance (
    id                   VARCHAR(36)  PRIMARY KEY,
    tenant_id            VARCHAR(36)  NOT NULL,
    approval_process_id  VARCHAR(36)  NOT NULL REFERENCES approval_process(id),
    collection_id        VARCHAR(36)  NOT NULL,
    record_id            VARCHAR(36)  NOT NULL,
    submitted_by         VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    current_step_number  INTEGER      NOT NULL DEFAULT 1,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    submitted_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED','RECALLED'))
);

CREATE TABLE approval_step_instance (
    id                    VARCHAR(36)  PRIMARY KEY,
    approval_instance_id  VARCHAR(36)  NOT NULL REFERENCES approval_instance(id) ON DELETE CASCADE,
    step_id               VARCHAR(36)  NOT NULL REFERENCES approval_step(id),
    assigned_to           VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    comments              TEXT,
    acted_at              TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_step_status CHECK (status IN ('PENDING','APPROVED','REJECTED','REASSIGNED'))
);

CREATE INDEX idx_approval_process_tenant ON approval_process(tenant_id, collection_id);
CREATE INDEX idx_approval_instance_record ON approval_instance(collection_id, record_id);
CREATE INDEX idx_approval_step_instance_assigned ON approval_step_instance(assigned_to, status);
