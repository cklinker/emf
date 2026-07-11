-- Telehealth slice 4: scheduling & visit links (specs/telehealth/4-scheduling.md).
-- Provider availability (weekly rules + date exceptions), appointments, and the
-- confirmation/reminder/cancellation email templates. Statements idempotent.
--
-- Double-booking is prevented in AppointmentService with a per-provider
-- transaction-scoped advisory lock + overlap check (no btree_gist exclusion
-- constraint — the standalone prod Postgres carries no extra extensions).

CREATE TABLE IF NOT EXISTS telehealth_availability (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    provider_id character varying(36) NOT NULL,
    kind character varying(20) DEFAULT 'RULE' NOT NULL,
    weekday integer,
    exception_date date,
    start_time time,
    end_time time,
    timezone character varying(50) DEFAULT 'UTC' NOT NULL,
    closed boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT telehealth_availability_pkey PRIMARY KEY (id),
    CONSTRAINT telehealth_availability_kind_check CHECK (kind IN ('RULE', 'EXCEPTION')),
    CONSTRAINT telehealth_availability_weekday_check
        CHECK (weekday IS NULL OR (weekday >= 0 AND weekday <= 6)),
    -- RULE rows need a weekday + window; EXCEPTION rows need a date.
    CONSTRAINT telehealth_availability_shape_check CHECK (
        (kind = 'RULE' AND weekday IS NOT NULL AND start_time IS NOT NULL AND end_time IS NOT NULL)
        OR
        (kind = 'EXCEPTION' AND exception_date IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_telehealth_availability_provider
    ON telehealth_availability (tenant_id, provider_id, active);

CREATE TABLE IF NOT EXISTS telehealth_appointment (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    provider_id character varying(36) NOT NULL,
    portal_user_id character varying(36) NOT NULL,
    scheduled_start timestamp with time zone NOT NULL,
    scheduled_end timestamp with time zone NOT NULL,
    status character varying(20) DEFAULT 'CONFIRMED' NOT NULL,
    visit_type character varying(100),
    reason character varying(500),
    conversation_id character varying(36),
    video_session_id character varying(36),
    reminder_sent_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT telehealth_appointment_pkey PRIMARY KEY (id),
    CONSTRAINT telehealth_appointment_status_check
        CHECK (status IN ('REQUESTED', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT telehealth_appointment_window_check CHECK (scheduled_end > scheduled_start)
);

CREATE INDEX IF NOT EXISTS idx_telehealth_appointment_provider_time
    ON telehealth_appointment (tenant_id, provider_id, scheduled_start);
CREATE INDEX IF NOT EXISTS idx_telehealth_appointment_portal_user
    ON telehealth_appointment (tenant_id, portal_user_id, scheduled_start);
-- Reminder sweep: due, unreminded, active appointments.
CREATE INDEX IF NOT EXISTS idx_telehealth_appointment_reminder
    ON telehealth_appointment (scheduled_start)
    WHERE reminder_sent_at IS NULL AND status = 'CONFIRMED';

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['telehealth_availability', 'telehealth_appointment'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE ONLY %I FORCE ROW LEVEL SECURITY', t);
        IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = t AND policyname = 'tenant_isolation') THEN
            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I USING (((tenant_id)::text = current_setting(''app.current_tenant_id''::text, true)))', t);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = t AND policyname = 'admin_bypass') THEN
            EXECUTE format(
                'CREATE POLICY admin_bypass ON %I USING ((current_setting(''app.current_tenant_id''::text, true) = ''''::text))', t);
        END IF;
    END LOOP;
END $$;

-- Email templates (system tenant; tenant-overridable by key). ${visitLink}
-- carries the signed HMAC visit token; the .ics rides as a real attachment.
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text,
                            is_active, created_by, created_at, updated_at, variables_schema, template_key)
SELECT gen_random_uuid()::text, 'system', 'Appointment Confirmed',
       'Sent when a telehealth appointment is booked; includes the visit link and a calendar (.ics) attachment',
       'Your appointment on ${startsAtLabel}',
       '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Appointment confirmed</h2><p>Hi ${firstName},</p><p>Your ${visitType} with ${providerName} is confirmed for <strong>${startsAtLabel}</strong>.</p><p style="margin:32px 0"><a href="${visitLink}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Open your visit</a></p><p style="color:#666;font-size:14px">A calendar invitation is attached. The visit link signs you in — do not forward this email.</p></body></html>',
       'Appointment confirmed\n\nHi ${firstName},\n\nYour ${visitType} with ${providerName} is confirmed for ${startsAtLabel}.\n\nOpen your visit: ${visitLink}\n\nA calendar invitation is attached. The visit link signs you in — do not forward this email.',
       true, 'system', now(), now(),
       '{"type": "object", "required": ["startsAtLabel", "visitLink"], "properties": {"firstName": {"type": "string"}, "providerName": {"type": "string"}, "visitType": {"type": "string"}, "startsAtLabel": {"type": "string"}, "visitLink": {"type": "string"}}}',
       'appointment.confirmed'
WHERE NOT EXISTS (SELECT 1 FROM email_template
                  WHERE tenant_id = 'system' AND template_key = 'appointment.confirmed');

INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text,
                            is_active, created_by, created_at, updated_at, variables_schema, template_key)
SELECT gen_random_uuid()::text, 'system', 'Appointment Reminder',
       'Sent one hour before a confirmed telehealth appointment',
       'Reminder: your appointment at ${startsAtLabel}',
       '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Your appointment is coming up</h2><p>Hi ${firstName},</p><p>Your ${visitType} with ${providerName} starts at <strong>${startsAtLabel}</strong>.</p><p style="margin:32px 0"><a href="${visitLink}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Open your visit</a></p><p style="color:#666;font-size:14px">The visit link signs you in — do not forward this email.</p></body></html>',
       'Your appointment is coming up\n\nHi ${firstName},\n\nYour ${visitType} with ${providerName} starts at ${startsAtLabel}.\n\nOpen your visit: ${visitLink}\n\nThe visit link signs you in — do not forward this email.',
       true, 'system', now(), now(),
       '{"type": "object", "required": ["startsAtLabel", "visitLink"], "properties": {"firstName": {"type": "string"}, "providerName": {"type": "string"}, "visitType": {"type": "string"}, "startsAtLabel": {"type": "string"}, "visitLink": {"type": "string"}}}',
       'appointment.reminder'
WHERE NOT EXISTS (SELECT 1 FROM email_template
                  WHERE tenant_id = 'system' AND template_key = 'appointment.reminder');

INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text,
                            is_active, created_by, created_at, updated_at, variables_schema, template_key)
SELECT gen_random_uuid()::text, 'system', 'Appointment Cancelled',
       'Sent when a telehealth appointment is cancelled',
       'Your appointment on ${startsAtLabel} was cancelled',
       '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Appointment cancelled</h2><p>Hi ${firstName},</p><p>Your ${visitType} with ${providerName} scheduled for <strong>${startsAtLabel}</strong> has been cancelled.</p><p style="color:#666;font-size:14px">If this is unexpected, please contact your provider to rebook.</p></body></html>',
       'Appointment cancelled\n\nHi ${firstName},\n\nYour ${visitType} with ${providerName} scheduled for ${startsAtLabel} has been cancelled.\n\nIf this is unexpected, please contact your provider to rebook.',
       true, 'system', now(), now(),
       '{"type": "object", "required": ["startsAtLabel"], "properties": {"firstName": {"type": "string"}, "providerName": {"type": "string"}, "visitType": {"type": "string"}, "startsAtLabel": {"type": "string"}}}',
       'appointment.cancelled'
WHERE NOT EXISTS (SELECT 1 FROM email_template
                  WHERE tenant_id = 'system' AND template_key = 'appointment.cancelled');
