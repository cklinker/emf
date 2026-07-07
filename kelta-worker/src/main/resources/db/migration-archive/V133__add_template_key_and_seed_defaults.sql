-- V133: Template-by-key lookup + system-default lifecycle templates.
--
-- Adds a stable `template_key` column to `email_template` so lifecycle code
-- can fetch a template without storing a UUID in source. Seeds eight default
-- templates under tenant_id='system'; tenant-owned rows with the same key
-- take precedence at lookup time.

ALTER TABLE email_template
    ADD COLUMN IF NOT EXISTS template_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_email_template_tenant_key
    ON email_template(tenant_id, template_key)
    WHERE template_key IS NOT NULL;

COMMENT ON COLUMN email_template.template_key IS
    'Stable identifier used by lifecycle code to look up a template (e.g. user.invite). '
    'NULL for ad-hoc workflow-only templates. Unique per tenant when set.';

-- Sentinel "system" tenant that owns the platform default templates.
-- Real tenants override by inserting a row with the same template_key and their tenant_id.
INSERT INTO tenant (id, slug, name, status, created_at, updated_at)
    VALUES ('system', 'system', 'System', 'ACTIVE', NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

-- Also need a platform_user 'system' for the created_by FK constraint.
INSERT INTO platform_user (id, tenant_id, email, username, first_name, last_name, status, created_at, updated_at)
    VALUES ('system', 'system', 'system@kelta.io', 'system', 'System', 'User', 'ACTIVE', NOW(), NOW())
    ON CONFLICT (id) DO NOTHING;

-- Common header/footer wrapper is repeated inline rather than abstracted —
-- tenants who override one template shouldn't be forced to inherit a global wrapper.

INSERT INTO email_template
    (id, tenant_id, template_key, name, subject, body_html, body_text, variables_schema, is_active, created_by, created_at, updated_at)
VALUES
    (gen_random_uuid()::text, 'system', 'user.invite',
     'User Invite',
     'You''re invited to ${tenantName}',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Welcome to ${tenantName}</h2><p>Hi ${firstName},</p><p>You''ve been invited to join <strong>${tenantName}</strong> on Kelta. Click the button below to set your password and activate your account.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Accept invite</a></p><p style="color:#666;font-size:14px">This invitation expires in ${expiresIn}. If the button doesn''t work, copy this link into your browser:<br><span style="word-break:break-all">${actionUrl}</span></p></body></html>',
     'Welcome to ${tenantName}\n\nHi ${firstName},\n\nYou''ve been invited to join ${tenantName} on Kelta. Visit the link below to set your password and activate your account.\n\n${actionUrl}\n\nThis invitation expires in ${expiresIn}.',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"email":{"type":"string"},"actionUrl":{"type":"string"},"expiresIn":{"type":"string"}},"required":["tenantName","firstName","actionUrl"]}'::jsonb,
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user.password_reset',
     'Password Reset Request',
     'Reset your ${tenantName} password',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Reset your password</h2><p>Hi ${firstName},</p><p>We received a request to reset the password for your ${tenantName} account. Click the button below to choose a new one.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Reset password</a></p><p style="color:#666;font-size:14px">This link expires in ${expiresIn}. If you didn''t request a reset, ignore this email.</p></body></html>',
     'Reset your password\n\nHi ${firstName},\n\nWe received a request to reset the password for your ${tenantName} account. Visit the link below to choose a new one.\n\n${actionUrl}\n\nThis link expires in ${expiresIn}. If you didn''t request a reset, ignore this email.',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"actionUrl":{"type":"string"},"expiresIn":{"type":"string"}},"required":["actionUrl"]}'::jsonb,
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user.password_changed',
     'Password Changed',
     'Your ${tenantName} password was changed',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Password changed</h2><p>Hi ${firstName},</p><p>The password for your ${tenantName} account was just changed.</p><p>If this was you, no further action is needed. If you didn''t change your password, contact your administrator immediately.</p><p style="color:#666;font-size:14px">When: ${changedAt}<br>From IP: ${ipAddress}</p></body></html>',
     'Password changed\n\nHi ${firstName},\n\nThe password for your ${tenantName} account was just changed.\n\nIf this was you, no further action is needed. If you didn''t change your password, contact your administrator immediately.\n\nWhen: ${changedAt}\nFrom IP: ${ipAddress}',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"changedAt":{"type":"string"},"ipAddress":{"type":"string"}}}'::jsonb,
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user.email_verification',
     'Verify your email',
     'Verify your email for ${tenantName}',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Verify your email</h2><p>Hi ${firstName},</p><p>Please confirm <strong>${email}</strong> is your email address for ${tenantName} by clicking the button below.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Verify email</a></p><p style="color:#666;font-size:14px">This link expires in ${expiresIn}.</p></body></html>',
     'Verify your email\n\nHi ${firstName},\n\nPlease confirm ${email} is your email address for ${tenantName} by visiting the link below.\n\n${actionUrl}\n\nThis link expires in ${expiresIn}.',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"email":{"type":"string"},"actionUrl":{"type":"string"},"expiresIn":{"type":"string"}},"required":["email","actionUrl"]}'::jsonb,
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user.mfa_enrolled',
     'MFA Enabled',
     'Two-factor authentication enabled on your ${tenantName} account',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Two-factor authentication enabled</h2><p>Hi ${firstName},</p><p>Two-factor authentication was just enabled on your ${tenantName} account.</p><p>If this wasn''t you, contact your administrator immediately.</p><p style="color:#666;font-size:14px">When: ${changedAt}</p></body></html>',
     'Two-factor authentication enabled\n\nHi ${firstName},\n\nTwo-factor authentication was just enabled on your ${tenantName} account.\n\nIf this wasn''t you, contact your administrator immediately.\n\nWhen: ${changedAt}',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"changedAt":{"type":"string"}}}'::jsonb,
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user.mfa_disabled',
     'MFA Disabled',
     'Two-factor authentication disabled on your ${tenantName} account',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Two-factor authentication disabled</h2><p>Hi ${firstName},</p><p>Two-factor authentication was just removed from your ${tenantName} account. Your account is now protected by password only.</p><p>If this wasn''t you, contact your administrator immediately and re-enable MFA.</p><p style="color:#666;font-size:14px">When: ${changedAt}</p></body></html>',
     'Two-factor authentication disabled\n\nHi ${firstName},\n\nTwo-factor authentication was just removed from your ${tenantName} account. Your account is now protected by password only.\n\nIf this wasn''t you, contact your administrator immediately and re-enable MFA.\n\nWhen: ${changedAt}',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"changedAt":{"type":"string"}}}'::jsonb,
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user.account_locked',
     'Account Locked',
     'Your ${tenantName} account has been locked',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Account locked</h2><p>Hi ${firstName},</p><p>Your ${tenantName} account has been locked after too many failed sign-in attempts.</p><p>The lock will be released at ${unlockAt}, or you can reset your password to unlock it sooner.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Reset password</a></p></body></html>',
     'Account locked\n\nHi ${firstName},\n\nYour ${tenantName} account has been locked after too many failed sign-in attempts.\n\nThe lock will be released at ${unlockAt}, or you can reset your password to unlock it sooner:\n\n${actionUrl}',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"unlockAt":{"type":"string"},"actionUrl":{"type":"string"}}}'::jsonb,
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user.welcome',
     'Welcome',
     'Welcome to ${tenantName}',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Welcome to ${tenantName}</h2><p>Hi ${firstName},</p><p>Your account is now active. We''re glad to have you on board.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Sign in</a></p></body></html>',
     'Welcome to ${tenantName}\n\nHi ${firstName},\n\nYour account is now active. We''re glad to have you on board.\n\nSign in: ${actionUrl}',
     '{"type":"object","properties":{"tenantName":{"type":"string"},"firstName":{"type":"string"},"actionUrl":{"type":"string"}}}'::jsonb,
     true, 'system', NOW(), NOW())
ON CONFLICT (tenant_id, template_key) WHERE template_key IS NOT NULL DO NOTHING;
