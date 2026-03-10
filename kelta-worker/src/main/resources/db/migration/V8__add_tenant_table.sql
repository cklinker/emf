-- Phase 1, Task A1: Tenant Database Migration
-- Creates the tenant table as the root entity for multi-tenant operations.
-- Every resource in the system belongs to exactly one tenant.

-- ============================================================================
-- TENANT TABLE
-- ============================================================================

CREATE TABLE tenant (
    id         VARCHAR(36)  PRIMARY KEY,
    slug       VARCHAR(63)  NOT NULL UNIQUE,
    name       VARCHAR(200) NOT NULL,
    edition    VARCHAR(20)  NOT NULL DEFAULT 'PROFESSIONAL',
    status     VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONING',
    settings   JSONB        NOT NULL DEFAULT '{}',
    limits     JSONB        NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tenant_edition CHECK (edition IN ('FREE', 'PROFESSIONAL', 'ENTERPRISE', 'UNLIMITED')),
    CONSTRAINT chk_tenant_status  CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'DECOMMISSIONED')),
    CONSTRAINT chk_tenant_slug    CHECK (slug ~ '^[a-z][a-z0-9-]{1,61}[a-z0-9]$')
);

-- Indexes for tenant table
CREATE INDEX idx_tenant_status ON tenant(status);
