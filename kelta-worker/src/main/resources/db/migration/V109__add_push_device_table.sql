-- V109: Push device registration for push notifications

CREATE TABLE push_device (
    id           VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id      VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    tenant_id    VARCHAR(36) NOT NULL,
    platform     VARCHAR(10) NOT NULL,  -- ios, android, web
    device_token VARCHAR(500) NOT NULL,
    device_name  VARCHAR(100),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_push_device_token UNIQUE (tenant_id, device_token)
);
CREATE INDEX idx_push_device_user ON push_device(user_id, tenant_id);
