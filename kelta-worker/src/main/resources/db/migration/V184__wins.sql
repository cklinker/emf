-- Consumer-alerting slice 9: win tracking + live ticker
-- (specs/consumer-alerting/9-win-tracking.md). A member confirms "I got the spot"; the claim
-- feeds per-target success stats and the live-wins ticker (the social-proof + retention engine).
--
-- NOTE ON NUMBERING: this is V184 because slice 8's V183 (analytics capture) is still an open
-- PR. **This migration must reach any database AFTER V183** — a lower-numbered migration that
-- arrives after a higher one already applied is silently skipped on existing DBs. Merge/deploy
-- slice 8 (#1313) before this slice.
--
-- Backed by the `wins` SYSTEM COLLECTION: members create wins through the owner-scoped
-- WinController; admins get JSON:API read + audit for free. target_id/watch_id/alert_id are
-- plain ids with NO FK — a win outlives what it references, and the alert ledger is pruned by
-- retention, so a cascade would delete history.

CREATE TABLE IF NOT EXISTS win (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    -- The claimant. FK to platform_user is via the collection's LOOKUP (SET NULL), created by
    -- the dynamic schema engine, not here.
    member_id character varying(36) NOT NULL,
    target_id character varying(36),
    watch_id character varying(36),
    alert_id character varying(36),
    category character varying(50),
    -- Human ticker text, e.g. "2 nights at Glacier — Many Glacier CG".
    summary character varying(280) NOT NULL,
    quantity integer,
    -- Gates whether this win appears on the public ticker/wins wall. Default private.
    is_public boolean DEFAULT false NOT NULL,
    -- Server-set FIRST NAME only, for the public feed — never more of the member's identity.
    claimant_name character varying(80),
    claimed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT win_pkey PRIMARY KEY (id)
);

-- A member's own wins, newest first (WinController list).
CREATE INDEX IF NOT EXISTS idx_win_member_time
    ON win (tenant_id, member_id, claimed_at);
-- The live-wins ticker: recent public wins, newest first.
CREATE INDEX IF NOT EXISTS idx_win_public_time
    ON win (tenant_id, claimed_at) WHERE is_public;
-- Per-target success stats (count + last win).
CREATE INDEX IF NOT EXISTS idx_win_target
    ON win (tenant_id, target_id);

ALTER TABLE win ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY win FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'win' AND policyname = 'tenant_isolation') THEN
        EXECUTE
            'CREATE POLICY tenant_isolation ON win '
            'USING (((tenant_id)::text = current_setting(''app.current_tenant_id''::text, true)))';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'win' AND policyname = 'admin_bypass') THEN
        EXECUTE
            'CREATE POLICY admin_bypass ON win '
            'USING ((current_setting(''app.current_tenant_id''::text, true) = ''''::text))';
    END IF;
END $$;
