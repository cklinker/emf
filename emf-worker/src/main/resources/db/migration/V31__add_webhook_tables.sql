-- V31: Webhook and delivery tables
-- Part of Phase 5 Stream B: Webhooks (5.2)

CREATE TABLE webhook (
    id               VARCHAR(36)   PRIMARY KEY,
    tenant_id        VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name             VARCHAR(200)  NOT NULL,
    url              VARCHAR(2048) NOT NULL,
    events           JSONB         NOT NULL,
    collection_id    VARCHAR(36)   REFERENCES collection(id),
    filter_formula   TEXT,
    headers          JSONB         DEFAULT '{}',
    secret           VARCHAR(200),
    active           BOOLEAN       DEFAULT true,
    retry_policy     JSONB         DEFAULT '{"maxRetries": 3, "backoffSeconds": [10, 60, 300]}',
    created_by       VARCHAR(36)   NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook UNIQUE (tenant_id, name)
);

CREATE TABLE webhook_delivery (
    id               VARCHAR(36)   PRIMARY KEY,
    webhook_id       VARCHAR(36)   NOT NULL REFERENCES webhook(id) ON DELETE CASCADE,
    event_type       VARCHAR(50)   NOT NULL,
    payload          JSONB         NOT NULL,
    response_status  INTEGER,
    response_body    TEXT,
    attempt_count    INTEGER       DEFAULT 1,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    next_retry_at    TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    delivered_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_delivery_status CHECK (status IN ('PENDING','DELIVERED','FAILED','RETRYING'))
);

CREATE INDEX idx_webhook_tenant ON webhook(tenant_id);
CREATE INDEX idx_webhook_collection ON webhook(collection_id);
CREATE INDEX idx_delivery_webhook ON webhook_delivery(webhook_id, created_at DESC);
CREATE INDEX idx_delivery_retry ON webhook_delivery(status, next_retry_at);
