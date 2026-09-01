-- Support mailbox slice 6: canned answers (specs/support-mailbox/README.md).
-- One table + RLS + indexes. Statements are idempotent.
--
-- mailbox_template holds MATCHING AND POLICY only. The copy lives in an
-- email_template row, referenced by template_key, which buys the tenant ->
-- 'system' fallback (EmailRepository.findTemplateByKey), the existing authoring
-- UI, email_log, and per-tenant SMTP for free.
--
-- auto_send_eligible is deliberately NOT a column on email_template. Putting it
-- there would place every invoice notice and campaign blast one boolean away
-- from being sent, unreviewed, to a stranger who emailed support.


CREATE TABLE IF NOT EXISTS mailbox_template (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,

    -- What this answers. Free text rather than an enum: the useful categories
    -- differ per product, and a CHECK constraint here would need a migration
    -- every time a tenant learns something about their own inbox.
    category character varying(60) NOT NULL,
    -- The email_template.template_key holding the copy.
    template_key character varying(200) NOT NULL,
    description text,

    -- ---------- Deterministic matching ----------
    -- Lowercased terms, one per array element. Deliberately not a regex: an
    -- author-supplied regex is an unbounded computation running against
    -- attacker-supplied text, which is a denial of service with extra steps.
    match_keywords jsonb DEFAULT '[]'::jsonb NOT NULL,
    -- Terms that veto this template no matter how well it otherwise scores.
    -- "refund" on a how-does-it-work answer, for instance.
    exclude_keywords jsonb DEFAULT '[]'::jsonb NOT NULL,
    priority integer DEFAULT 100 NOT NULL,

    -- ---------- Auto-send policy ----------
    -- FALSE by default and only ever raised deliberately. A template is not
    -- auto-sendable because it happens to match; someone has to say so.
    auto_send_eligible boolean DEFAULT false NOT NULL,
    min_confidence numeric(4,3) DEFAULT 0.900 NOT NULL,
    requires_verified_sender boolean DEFAULT false NOT NULL,
    -- Set by the author when the copy references anything about the requester's
    -- account. Blocks auto-send to an unverified sender outright.
    discloses_account_data boolean DEFAULT false NOT NULL,

    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_template_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_template_unique UNIQUE (mailbox_id, category),
    CONSTRAINT mailbox_template_confidence_check
        CHECK (min_confidence >= 0 AND min_confidence <= 1),
    CONSTRAINT mailbox_template_mailbox_fk FOREIGN KEY (mailbox_id)
        REFERENCES mailbox (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mailbox_template_mailbox
    ON mailbox_template (tenant_id, mailbox_id, active, priority);


DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['mailbox_template'] LOOP
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
