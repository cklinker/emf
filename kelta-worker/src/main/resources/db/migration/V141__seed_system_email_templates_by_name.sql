-- V141: Seed system-level email templates resolvable by `name` column.
--
-- V133 added templates keyed by `template_key` (e.g. user.password_reset).
-- This migration adds three short-named system templates that callers using
-- the simpler `name` lookup (password_reset, user_invite, welcome) can hit
-- directly, with the same tenant-override-then-system fallback semantics.
--
-- Why tenant_id = 'system' and not NULL: the email_template.tenant_id column
-- has NOT NULL + FK(tenant) since V25, and V133 already established 'system'
-- as the sentinel tenant for platform defaults. Reusing that convention here
-- avoids a schema change to nullable while preserving identical fallback
-- behaviour (see EmailRepository.findTemplateByName).

INSERT INTO email_template
    (id, tenant_id, name, subject, body_html, body_text, is_active, created_by, created_at, updated_at)
VALUES
    (gen_random_uuid()::text, 'system', 'password_reset',
     'Reset your password',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Reset your password</h2><p>We received a request to reset your password. Click the link below to choose a new one.</p><p style="margin:32px 0"><a href="${resetLink}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Reset password</a></p><p style="color:#666;font-size:14px">If you didn''t request a reset, you can safely ignore this email.</p></body></html>',
     'Reset your password\n\nWe received a request to reset your password. Visit the link below to choose a new one.\n\n${resetLink}\n\nIf you didn''t request a reset, you can safely ignore this email.',
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'user_invite',
     'You''ve been invited',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>You''ve been invited to ${tenantName}</h2><p>You''ve been invited to join <strong>${tenantName}</strong>. Click the link below to accept the invitation and set up your account.</p><p style="margin:32px 0"><a href="${inviteLink}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Accept invitation</a></p></body></html>',
     'You''ve been invited to ${tenantName}\n\nYou''ve been invited to join ${tenantName}. Visit the link below to accept the invitation and set up your account.\n\n${inviteLink}',
     true, 'system', NOW(), NOW()),

    (gen_random_uuid()::text, 'system', 'welcome',
     'Welcome to ${tenantName}',
     '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Welcome, ${userName}!</h2><p>Your account is now active. We''re glad to have you on board.</p></body></html>',
     'Welcome, ${userName}!\n\nYour account is now active. We''re glad to have you on board.',
     true, 'system', NOW(), NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;
