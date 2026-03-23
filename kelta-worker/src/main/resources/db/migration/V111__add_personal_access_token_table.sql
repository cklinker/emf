-- Personal Access Tokens (PAT) for programmatic API access
CREATE TABLE user_api_token (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id         VARCHAR(36) NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name            VARCHAR(200) NOT NULL,
    token_prefix    VARCHAR(10) NOT NULL,
    token_hash      VARCHAR(500) NOT NULL,
    scopes          JSONB DEFAULT '["api"]',
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    revoked         BOOLEAN DEFAULT false,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_user_api_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_user_api_token_name_per_user UNIQUE (user_id, name)
);

CREATE INDEX idx_user_api_token_user ON user_api_token(user_id);
CREATE INDEX idx_user_api_token_tenant ON user_api_token(tenant_id);
CREATE INDEX idx_user_api_token_hash_active ON user_api_token(token_hash) WHERE revoked = false;
CREATE INDEX idx_user_api_token_prefix_active ON user_api_token(token_prefix) WHERE revoked = false;
