-- Stores captured HTTP request/response data for observability.
-- Previously stored in OpenSearch (kelta-request-data-* index).
CREATE TABLE request_data (
    id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    tenant_id        VARCHAR(36) NOT NULL REFERENCES tenant(id),
    trace_id         VARCHAR(64) NOT NULL,
    span_id          VARCHAR(32),
    method           VARCHAR(10) NOT NULL,
    path             TEXT NOT NULL,
    status_code      INTEGER NOT NULL,
    user_id          VARCHAR(36),
    user_email       VARCHAR(320),
    correlation_id   VARCHAR(64),
    request_headers  JSONB,
    response_headers JSONB,
    request_body     TEXT,
    response_body    TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_request_data_tenant_created ON request_data(tenant_id, created_at DESC);
CREATE INDEX idx_request_data_trace ON request_data(trace_id);
