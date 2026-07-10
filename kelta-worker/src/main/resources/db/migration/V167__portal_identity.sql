-- Telehealth slice 1: portal identity (specs/telehealth/1-portal-identity.md).
-- Adds the INTERNAL|PORTAL user-type split, the passwordless portal login-token
-- table, the seeded "Portal User" profile, and the portal email templates.
-- Runs under Flyway with app.current_tenant_id unset -> the admin_bypass RLS
-- policy permits the cross-tenant writes. Every statement is idempotent.

-- ---------------------------------------------------------------------------
-- 1. platform_user.user_type
-- ---------------------------------------------------------------------------
ALTER TABLE platform_user
    ADD COLUMN IF NOT EXISTS user_type character varying(20) NOT NULL DEFAULT 'INTERNAL';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'platform_user_user_type_check') THEN
        ALTER TABLE platform_user
            ADD CONSTRAINT platform_user_user_type_check
            CHECK (user_type IN ('INTERNAL', 'PORTAL'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_platform_user_tenant_user_type
    ON platform_user (tenant_id, user_type);

-- ---------------------------------------------------------------------------
-- 2. Seed the "Portal User" profile for every existing tenant that has profiles.
--    Grants API_ACCESS only; absent profile_system_permission rows read as not
--    granted (same single-row seeding approach as V162 VIEW_ANALYTICS).
--    TenantProvisioningHook seeds it for tenants created after this migration.
-- ---------------------------------------------------------------------------
INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at)
SELECT gen_random_uuid()::text,
       t.tenant_id,
       'Portal User',
       'External portal user (telehealth patient) — login and API access only; data access is granted per record via participant shares',
       TRUE, now(), now()
FROM (SELECT DISTINCT tenant_id FROM profile) t
WHERE NOT EXISTS (SELECT 1 FROM profile p
                  WHERE p.tenant_id = t.tenant_id AND p.name = 'Portal User');

INSERT INTO profile_system_permission (id, tenant_id, profile_id, permission_name, granted, created_at, updated_at)
SELECT gen_random_uuid()::text, p.tenant_id, p.id, 'API_ACCESS', TRUE, now(), now()
FROM profile p
WHERE p.name = 'Portal User'
  AND NOT EXISTS (SELECT 1 FROM profile_system_permission x
                  WHERE x.profile_id = p.id AND x.permission_name = 'API_ACCESS');

-- ---------------------------------------------------------------------------
-- 3. Portal login tokens (magic links). Stores SHA-256 hashes only — the raw
--    token appears exclusively in the emailed link. PORTAL_LOGIN tokens live
--    15 minutes; PORTAL_INVITE tokens (first-access link in the invite email)
--    live 7 days. consumed_at enforces single use.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS portal_login_token (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    token_hash character varying(64) NOT NULL,
    purpose character varying(20) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT portal_login_token_pkey PRIMARY KEY (id),
    CONSTRAINT portal_login_token_purpose_check CHECK (purpose IN ('PORTAL_LOGIN', 'PORTAL_INVITE')),
    CONSTRAINT portal_login_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_portal_login_token_rate
    ON portal_login_token (tenant_id, user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_portal_login_token_expiry
    ON portal_login_token (expires_at);

ALTER TABLE portal_login_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY portal_login_token FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'portal_login_token' AND policyname = 'tenant_isolation') THEN
        CREATE POLICY tenant_isolation ON portal_login_token
            USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'portal_login_token' AND policyname = 'admin_bypass') THEN
        CREATE POLICY admin_bypass ON portal_login_token
            USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 4. Portal email templates (system tenant; tenants may override by key).
-- ---------------------------------------------------------------------------
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text,
                            is_active, created_by, created_at, updated_at, variables_schema, template_key)
SELECT gen_random_uuid()::text, 'system', 'Portal Invite',
       'Sent when an external portal user is invited; the link signs them in directly (single use, 7 days)',
       'You now have portal access at ${tenantName}',
       '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Welcome to ${tenantName}</h2><p>Hi ${firstName},</p><p>You have been given access to the <strong>${tenantName}</strong> portal. Use the button below to sign in — no password needed.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Open your portal</a></p><p style="color:#666;font-size:14px">This link works once and expires in ${expiresIn}. After that, request a new sign-in link from the portal login page. If the button does not work, copy this link into your browser:<br><span style="word-break:break-all">${actionUrl}</span></p></body></html>',
       'Welcome to ${tenantName}\n\nHi ${firstName},\n\nYou have been given access to the ${tenantName} portal. Open the link below to sign in — no password needed.\n\n${actionUrl}\n\nThis link works once and expires in ${expiresIn}.',
       true, 'system', now(), now(),
       '{"type": "object", "required": ["tenantName", "actionUrl"], "properties": {"actionUrl": {"type": "string"}, "expiresIn": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}',
       'portal.invite'
WHERE NOT EXISTS (SELECT 1 FROM email_template
                  WHERE tenant_id = 'system' AND template_key = 'portal.invite');

INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text,
                            is_active, created_by, created_at, updated_at, variables_schema, template_key)
SELECT gen_random_uuid()::text, 'system', 'Portal Sign-in Link',
       'Passwordless sign-in link for portal users (single use, 15 minutes)',
       'Your sign-in link for ${tenantName}',
       '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Sign in to ${tenantName}</h2><p>Use the button below to sign in to your portal.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Sign in</a></p><p style="color:#666;font-size:14px">This link works once and expires in ${expiresIn}. If you did not request it, you can ignore this email — your account is unaffected. If the button does not work, copy this link into your browser:<br><span style="word-break:break-all">${actionUrl}</span></p></body></html>',
       'Sign in to ${tenantName}\n\nOpen the link below to sign in to your portal.\n\n${actionUrl}\n\nThis link works once and expires in ${expiresIn}. If you did not request it, ignore this email.',
       true, 'system', now(), now(),
       '{"type": "object", "required": ["tenantName", "actionUrl"], "properties": {"actionUrl": {"type": "string"}, "expiresIn": {"type": "string"}, "tenantName": {"type": "string"}}}',
       'portal.login-link'
WHERE NOT EXISTS (SELECT 1 FROM email_template
                  WHERE tenant_id = 'system' AND template_key = 'portal.login-link');
