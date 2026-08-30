-- tenant_module.installed_by was character varying(36) — sized for a UUID, but the column
-- actually stores whatever the gateway stamps as X-User-ID, which in this deployment is the
-- actor's email. Any identifier over 36 characters therefore failed the install outright:
--
--   ERROR: value too long for type character varying(36)
--
-- Latent until an actor's identifier grew past 36. It surfaced installing a module as a sandbox
-- admin, whose generated email is derived from the (long) sandbox slug —
-- spotopened--billing-stripe-verify-admin@kelta.local is 51 characters — while the production
-- admin, spotopened-admin@kelta.local, is 28 and fits.
--
-- 255 matches every other actor column in the module tables (tenant_module_action.created_by /
-- updated_by, tenant_module_signing_key.created_by / updated_by), so this removes an outlier
-- rather than inventing a new width. Widening is not destructive: no value is truncated, no
-- index or constraint references the column.
ALTER TABLE tenant_module
    ALTER COLUMN installed_by TYPE character varying(255);
