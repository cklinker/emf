-- Per-tenant module JAR signing keys, with rotation.
--
-- Replaces the single platform-wide trust anchor (kelta.modules.signing.public-key) as the
-- primary mechanism. That property could only answer "did the operator's one key sign this",
-- so every tenant wanting a module either routed its JAR through the operator or was handed
-- the operator's private key — and a signature could not distinguish one tenant's publisher
-- from another's. Per-tenant anchors make a JAR signed for tenant A unloadable in tenant B.
--
-- The platform already had this pattern twice (per-tenant Stripe webhook secrets via the
-- credential resolver; per-tenant SAML verification certs). Modules were the outlier.
--
-- MULTIPLE ACTIVE KEYS PER TENANT is the point, not an accident: a signature verifies if it
-- matches ANY active key. That makes rotation non-breaking —
--   1. add the new key (both now active)
--   2. re-sign and re-install modules at leisure
--   3. retire the old key once nothing depends on it
-- With one key per tenant, step 1 would invalidate every installed module at once, because a
-- JAR is re-verified on EVERY load, not only at install.
--
-- Public keys, so this is a plain table and not a secret store: there is nothing here that
-- needs hiding, and a Kubernetes Secret cannot be per-tenant anyway. The private halves never
-- enter the cluster.

CREATE TABLE IF NOT EXISTS tenant_module_signing_key (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    -- Operator-facing name used to talk about rotation ("2026-h2", "ci-runner").
    label character varying(100) NOT NULL,
    -- Per-key, not per-platform: an Ed25519 key and an RSA key can be active at the same
    -- time, so migrating algorithm is the same non-breaking rotation as migrating key.
    algorithm character varying(40) DEFAULT 'Ed25519' NOT NULL,
    public_key_pem text NOT NULL,
    -- SHA-256 over the DER SPKI bytes, hex. Names a key in logs, API responses and on
    -- tenant_module without moving the PEM around, and is stable across PEM reformatting.
    fingerprint character varying(64) NOT NULL,
    -- Retirement is a flag, never a DELETE: the fingerprint on tenant_module has to stay
    -- resolvable to explain why an installed module suddenly loads as a stub.
    active boolean DEFAULT true NOT NULL,
    retired_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT tenant_module_signing_key_pkey PRIMARY KEY (id),
    -- Adding the same key twice would double-count it when reporting what still depends on
    -- a key before retirement.
    CONSTRAINT tenant_module_signing_key_fingerprint_key UNIQUE (tenant_id, fingerprint),
    CONSTRAINT tenant_module_signing_key_label_key UNIQUE (tenant_id, label)
);

-- The verification hot path: "active keys for this tenant", read on every install and on
-- every module load at pod startup.
CREATE INDEX IF NOT EXISTS idx_tenant_module_signing_key_active
    ON tenant_module_signing_key (tenant_id, active);

-- Which key verified this module's signature at install.
--
-- Load-bearing for rotation rather than decorative: retiring a key silently degrades every
-- module signed only by it to inert stub handlers on the next pod restart (a failed
-- verification falls back to stubs, and /api/modules still reports ACTIVE). This column is
-- what lets the API answer "N modules still depend on this key" BEFORE it is retired.
ALTER TABLE tenant_module
    ADD COLUMN IF NOT EXISTS jar_signature_key_fingerprint character varying(64);

-- Deliberately no FK to tenant_module_signing_key: the fingerprint must survive a key row
-- being removed, which is exactly the situation it exists to diagnose.
CREATE INDEX IF NOT EXISTS idx_tenant_module_signature_key
    ON tenant_module (tenant_id, jar_signature_key_fingerprint);

ALTER TABLE tenant_module_signing_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY tenant_module_signing_key FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'tenant_module_signing_key'
                     AND policyname = 'tenant_isolation') THEN
        EXECUTE
            'CREATE POLICY tenant_isolation ON tenant_module_signing_key '
            'USING (((tenant_id)::text = current_setting(''app.current_tenant_id''::text, true)))';
    END IF;
    -- Module loading at pod startup sweeps every tenant with no tenant context bound, so it
    -- needs the same empty-setting bypass the rest of the schema uses. Every query in
    -- JdbcModuleSigningKeyStore still filters on tenant_id explicitly — the bypass widens
    -- what RLS permits, so it must not be the only thing scoping a read.
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'tenant_module_signing_key'
                     AND policyname = 'admin_bypass') THEN
        EXECUTE
            'CREATE POLICY admin_bypass ON tenant_module_signing_key '
            'USING ((current_setting(''app.current_tenant_id''::text, true) = ''''::text))';
    END IF;
END $$;
