-- V25: Email templates and email log tables
-- Part of Phase 4 Stream A: Email Templates & Alerts (4.6)

CREATE TABLE email_template (
    id                     VARCHAR(36)   PRIMARY KEY,
    tenant_id              VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name                   VARCHAR(200)  NOT NULL,
    description            VARCHAR(500),
    subject                VARCHAR(500)  NOT NULL,
    body_html              TEXT          NOT NULL,
    body_text              TEXT,
    related_collection_id  VARCHAR(36)   REFERENCES collection(id),
    folder                 VARCHAR(100),
    is_active              BOOLEAN       DEFAULT true,
    created_by             VARCHAR(36)   NOT NULL REFERENCES platform_user(id),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_email_template UNIQUE (tenant_id, name)
);

CREATE TABLE email_log (
    id              VARCHAR(36)   PRIMARY KEY,
    tenant_id       VARCHAR(36)   NOT NULL,
    template_id     VARCHAR(36)   REFERENCES email_template(id),
    recipient_email VARCHAR(320)  NOT NULL,
    subject         VARCHAR(500)  NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'QUEUED',
    source          VARCHAR(30),
    source_id       VARCHAR(36),
    error_message   TEXT,
    sent_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_template_tenant ON email_template(tenant_id);
CREATE INDEX idx_email_log_tenant ON email_log(tenant_id, status);
CREATE INDEX idx_email_log_template ON email_log(template_id);
