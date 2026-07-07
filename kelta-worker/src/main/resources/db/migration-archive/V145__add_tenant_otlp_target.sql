-- Rec 7: self-service per-tenant OTLP trace-export target.
--
-- One row per tenant: where to forward that tenant's spans (endpoint + optional
-- headers, which may carry an auth token — hence RLS). Read by the worker's
-- DbTenantOtlpRegistry (under that tenant's context) and written by the admin
-- endpoint. Absence / enabled=false → that tenant's spans only go to the platform
-- collector.
CREATE TABLE tenant_otlp_target (
    tenant_id   VARCHAR(36) PRIMARY KEY,
    endpoint    VARCHAR(1000) NOT NULL,
    headers     JSONB,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE tenant_otlp_target ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_otlp_target FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_otlp_target;
DROP POLICY IF EXISTS admin_bypass ON tenant_otlp_target;
CREATE POLICY tenant_isolation ON tenant_otlp_target
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON tenant_otlp_target
    USING (current_setting('app.current_tenant_id', true) = '');
