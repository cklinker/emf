-- SCIM 2.0 client tokens for identity provider provisioning
CREATE TABLE scim_client (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scim_client_tenant ON scim_client(tenant_id);
CREATE INDEX idx_scim_client_token_hash ON scim_client(token_hash);
