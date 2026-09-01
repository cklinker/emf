-- Support mailbox slice 4: SLA escalation (specs/support-mailbox/README.md).
-- Three tables + RLS + indexes. Statements are idempotent.
--
-- Reuses the PATTERN of alert/alert_delivery (V179) and deliberately not the
-- TABLES. `alert` requires NOT NULL watch_id / target_id / slot_key / episode_id
-- and its unique key is (tenant_id, watch_id, slot_key, episode_id); a support
-- escalation has none of those, so reuse would mean inventing sentinel values
-- that pollute the availability dedupe ledger and the win-tracking joins that
-- read it. `alert_delivery` is worse: it has ON DELETE CASCADE to `alert` and
-- deliberately carries no tenant_id because it is only reachable through a
-- tenant-scoped alert. Neither holds here.


-- ---------------------------------------------------------------------------
-- mailbox_escalation — one row per (thread, level) that has fired.
--
-- The INSERT is both the claim and the dedupe, which is the whole design: the
-- unique constraint means concurrent pods race for the same row, exactly one
-- wins, and the losers get zero rows back and send nothing. No leader election,
-- no advisory lock, no "have we sent this?" read that can race.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_escalation (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,
    thread_id character varying(36) NOT NULL,
    -- Which clock breached. A thread can escalate independently on first
    -- response and on resolution, and conflating them would let a fast first
    -- reply suppress the alarm on a conversation that then goes unresolved.
    clock character varying(20) NOT NULL,
    level character varying(20) NOT NULL,
    -- The due time as it stood when this fired, copied rather than joined: the
    -- thread's due time can later be recomputed, and an escalation record that
    -- silently changes its own justification is not a record.
    sla_due_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_escalation_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_escalation_clock_check CHECK (clock IN ('FIRST_RESPONSE', 'RESOLUTION')),
    CONSTRAINT mailbox_escalation_level_check CHECK (level IN ('WARN', 'BREACH', 'BREACH_2', 'BREACH_3')),
    -- The anti-spam guarantee, enforced by the database rather than by sweep logic.
    CONSTRAINT mailbox_escalation_dedupe UNIQUE (tenant_id, thread_id, clock, level),
    CONSTRAINT mailbox_escalation_thread_fk FOREIGN KEY (thread_id)
        REFERENCES mailbox_thread (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- mailbox_escalation_delivery — what was owed, and what happened.
--
-- Rows are written PENDING before any send is attempted, so a crash mid-dispatch
-- leaves evidence of an unmet obligation rather than losing it silently.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_escalation_delivery (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    escalation_id character varying(36) NOT NULL,
    recipient_user_id character varying(36),
    channel character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'PENDING' NOT NULL,
    sent_at timestamp with time zone,
    error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_escalation_delivery_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_escalation_delivery_channel_check CHECK (channel IN ('email', 'push', 'sms')),
    CONSTRAINT mailbox_escalation_delivery_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT mailbox_escalation_delivery_fk FOREIGN KEY (escalation_id)
        REFERENCES mailbox_escalation (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- mailbox_escalation_contact — who to tell, per level.
--
-- A table rather than columns on `mailbox` because escalation is a chain: level
-- 1 goes to the team, level 3 to whoever owns the outcome. Columns would cap the
-- chain at whatever we guessed today.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_escalation_contact (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,
    level character varying(20) NOT NULL,
    user_id character varying(36) NOT NULL,
    -- JSON array, e.g. ["email","push"]. SMS is billable, so it is opt-in per
    -- contact rather than implied by the level.
    channels jsonb DEFAULT '["email"]'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_escalation_contact_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_escalation_contact_level_check CHECK (level IN ('WARN', 'BREACH', 'BREACH_2', 'BREACH_3')),
    CONSTRAINT mailbox_escalation_contact_unique UNIQUE (mailbox_id, level, user_id),
    CONSTRAINT mailbox_escalation_contact_mailbox_fk FOREIGN KEY (mailbox_id)
        REFERENCES mailbox (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- mailbox_thread_read — per-user unread state.
--
-- Deferred out of V191 on purpose: the per-(user, thread) read fact has no home
-- on a per-mailbox access row, and the console is the first thing that needs it.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_thread_read (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    thread_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    last_read_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_thread_read_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_thread_read_unique UNIQUE (thread_id, user_id),
    CONSTRAINT mailbox_thread_read_thread_fk FOREIGN KEY (thread_id)
        REFERENCES mailbox_thread (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_mailbox_escalation_thread
    ON mailbox_escalation (tenant_id, thread_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mailbox_escalation_delivery_escalation
    ON mailbox_escalation_delivery (tenant_id, escalation_id);
CREATE INDEX IF NOT EXISTS idx_mailbox_escalation_contact_mailbox
    ON mailbox_escalation_contact (tenant_id, mailbox_id, level);
CREATE INDEX IF NOT EXISTS idx_mailbox_thread_read_user
    ON mailbox_thread_read (tenant_id, user_id, thread_id);


-- ---------------------------------------------------------------------------
-- RLS — tenant_isolation + admin_bypass, per V168:98-112.
-- admin_bypass is what lets the cross-tenant escalation sweep run unbound.
-- ---------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['mailbox_escalation', 'mailbox_escalation_delivery',
                             'mailbox_escalation_contact', 'mailbox_thread_read'] LOOP
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


-- ---------------------------------------------------------------------------
-- The escalation email body. Tenant-overridable via the usual tenant -> 'system'
-- fallback in EmailRepository.findTemplateByKey.
-- ---------------------------------------------------------------------------
INSERT INTO email_template
    (id, tenant_id, name, description, subject, body_html, body_text, template_key,
     is_active, created_at, updated_at)
SELECT gen_random_uuid()::text, 'system', 'Support SLA escalation',
       'Sent when a support thread passes its SLA warning or breach threshold.',
       '[${level}] ${mailboxName}: ${subject}',
       '<p>A support thread has passed its <strong>${clockLabel}</strong> SLA threshold.</p>'
       || '<p><strong>Mailbox:</strong> ${mailboxName}<br>'
       || '<strong>From:</strong> ${requesterEmail}<br>'
       || '<strong>Subject:</strong> ${subject}<br>'
       || '<strong>Due:</strong> ${dueAt}</p>'
       || '<p><a href="${threadUrl}">Open the thread</a></p>',
       NULL,
       'support.sla_escalation',
       true, now(), now()
WHERE NOT EXISTS (
    SELECT 1 FROM email_template
     WHERE tenant_id = 'system' AND template_key = 'support.sla_escalation');
