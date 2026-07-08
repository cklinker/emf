-- Tenant-authored UI translations (app-intelligence slice 4): one row per
-- (tenant, locale, key) overriding the static locale bundles. Served by the
-- generic dynamic collection route (ui-translations); merged client-side as an
-- overlay in I18nContext (tenant value > locale bundle > en bundle).
CREATE TABLE ui_translation (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    locale character varying(10) NOT NULL,
    translation_key character varying(200) NOT NULL,
    translation_value character varying(2000) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT ui_translation_pkey PRIMARY KEY (id),
    CONSTRAINT ui_translation_locale_key UNIQUE (tenant_id, locale, translation_key)
);

CREATE INDEX idx_ui_translation_tenant_locale
    ON ui_translation (tenant_id, locale);

ALTER TABLE ui_translation ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY ui_translation FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ui_translation
    USING ((tenant_id)::text = current_setting('app.current_tenant_id'::text, true));
CREATE POLICY admin_bypass ON ui_translation
    USING (current_setting('app.current_tenant_id'::text, true) = ''::text);
