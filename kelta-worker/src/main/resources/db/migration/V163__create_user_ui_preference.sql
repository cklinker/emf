-- Per-user UI preferences (app-data-entry slice 1): one row per
-- (tenant, user, prefType, prefKey) holding a JSON value — saved list views,
-- favorites, recents. Served by the generic dynamic collection route
-- (user-ui-preferences); writes are owner-guarded by UserPreferenceGuardHook.
CREATE TABLE user_ui_preference (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    pref_type character varying(30) NOT NULL,
    pref_key character varying(200) DEFAULT '-' NOT NULL,
    value jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT user_ui_preference_pkey PRIMARY KEY (id),
    CONSTRAINT user_ui_preference_owner_key UNIQUE (tenant_id, user_id, pref_type, pref_key)
);

CREATE INDEX idx_user_ui_preference_owner
    ON user_ui_preference (tenant_id, user_id, pref_type);

ALTER TABLE user_ui_preference ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY user_ui_preference FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON user_ui_preference
    USING ((tenant_id)::text = current_setting('app.current_tenant_id'::text, true));
CREATE POLICY admin_bypass ON user_ui_preference
    USING (current_setting('app.current_tenant_id'::text, true) = ''::text);
