-- V42: Create note and file_attachment tables for record collaboration
-- Notes: Text annotations attached to records by users
-- FileAttachment: Metadata for files attached to records (S3 upload deferred)

CREATE TABLE IF NOT EXISTS note (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36)  NOT NULL REFERENCES collection(id),
    record_id     VARCHAR(36)  NOT NULL,
    content       TEXT         NOT NULL,
    created_by    VARCHAR(320) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_note_record ON note(collection_id, record_id, created_at DESC);
CREATE INDEX idx_note_tenant ON note(tenant_id);

CREATE TABLE IF NOT EXISTS file_attachment (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36)  NOT NULL REFERENCES collection(id),
    record_id     VARCHAR(36)  NOT NULL,
    file_name     VARCHAR(500) NOT NULL,
    file_size     BIGINT       NOT NULL DEFAULT 0,
    content_type  VARCHAR(200) NOT NULL,
    storage_key   VARCHAR(500),
    uploaded_by   VARCHAR(320) NOT NULL,
    uploaded_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attachment_record ON file_attachment(collection_id, record_id, uploaded_at DESC);
CREATE INDEX idx_attachment_tenant ON file_attachment(tenant_id);
