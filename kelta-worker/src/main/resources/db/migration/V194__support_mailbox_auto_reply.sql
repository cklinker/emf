-- Support mailbox slice 7: auto-reply decisions (specs/support-mailbox/README.md).
-- One table + RLS + indexes + escalation-contact seeding helper. Idempotent.
--
-- Every inbound message gets exactly one decision row, whether or not anything
-- was sent. That is the point of the table: shadow mode records what WOULD have
-- happened, and the only way to know whether auto-reply is safe to switch on is
-- to compare a fortnight of those decisions against what humans actually sent.
-- A boolean "did we auto-reply" column on the message would answer nothing.


CREATE TABLE IF NOT EXISTS mailbox_auto_reply_decision (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,
    thread_id character varying(36) NOT NULL,
    -- The inbound message the decision was made about. UNIQUE, which is also the
    -- claim: whichever pod inserts first owns the decision and the rest stop.
    message_id character varying(36) NOT NULL,

    -- SENT | SHADOW | VETOED. SHADOW means we would have sent and deliberately
    -- did not, which is a different fact from being vetoed and must stay
    -- distinguishable or the shadow comparison is worthless.
    outcome character varying(20) NOT NULL,
    -- Why not, when not. Null on SENT.
    veto_reason character varying(60),

    -- What the deterministic matcher chose, recorded even when vetoed — a veto on
    -- a correct match and a veto on a wrong one are different problems.
    matched_template_id character varying(36),
    matched_category character varying(60),
    confidence numeric(4,3),
    ambiguous boolean DEFAULT false NOT NULL,

    -- The outbound message, when one was actually sent.
    reply_message_id character varying(36),

    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_auto_reply_decision_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_auto_reply_decision_outcome_check
        CHECK (outcome IN ('SENT', 'SHADOW', 'VETOED')),
    -- Decide once per inbound message, fleet-wide.
    CONSTRAINT mailbox_auto_reply_decision_unique UNIQUE (tenant_id, message_id),
    CONSTRAINT mailbox_auto_reply_decision_thread_fk FOREIGN KEY (thread_id)
        REFERENCES mailbox_thread (id) ON DELETE CASCADE
);

-- The reporting query: outcomes per mailbox over a window.
CREATE INDEX IF NOT EXISTS idx_mailbox_auto_reply_decision_mailbox
    ON mailbox_auto_reply_decision (tenant_id, mailbox_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mailbox_auto_reply_decision_outcome
    ON mailbox_auto_reply_decision (tenant_id, mailbox_id, outcome, created_at DESC);


-- ---------------------------------------------------------------------------
-- Daily send budget. A column rather than a config property so an operator can
-- change it per mailbox without a deploy, and so the kill switch and the budget
-- live in the same place an admin already looks.
-- ---------------------------------------------------------------------------
ALTER TABLE mailbox ADD COLUMN IF NOT EXISTS max_auto_replies_per_day integer DEFAULT 200 NOT NULL;

-- Categories that must never be auto-answered regardless of how well a template
-- matches. Seeded rather than hardcoded so a tenant can add their own, and
-- deliberately a denylist: a new category is not auto-sendable until someone
-- says it is, which is the safe default for a list that grows.
ALTER TABLE mailbox ADD COLUMN IF NOT EXISTS auto_reply_blocked_categories jsonb
    DEFAULT '["billing","refund","cancellation","legal","security","complaint","account_access"]'::jsonb NOT NULL;


DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['mailbox_auto_reply_decision'] LOOP
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
