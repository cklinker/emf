-- Consumer-alerting slice 3: watch / target model
-- (specs/consumer-alerting/3-watch-model.md). A member registers a WATCH against
-- a TARGET; an external poller reports availability; the matcher (slice 4) turns
-- transitions into alerts.
--
-- Two tiers on purpose:
--   * watch_target / watch are backed by SYSTEM COLLECTIONS — admins and flows get
--     JSON:API, validation, and audit for free, and the matcher gets a fixed shape
--     it can index and push predicates into.
--   * availability_state / alert / alert_delivery are matcher-internal hot-path
--     tables written by direct JDBC. They are deliberately NOT collections: they
--     carry no admin surface and would only add per-write overhead.
--
-- Raw availability observations are deliberately NOT persisted — only the derived
-- state transition is. Storing every poll of every target is unbounded growth for
-- data nothing reads.

-- Something watchable: a campsite, an interview slot pool, a race, a permit.
-- Identified by (source, external_id) so two sources reusing the same upstream id
-- cannot collide.
CREATE TABLE IF NOT EXISTS watch_target (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    source character varying(50) NOT NULL,
    external_id character varying(200) NOT NULL,
    name character varying(255) NOT NULL,
    category character varying(50),
    metadata jsonb,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT watch_target_pkey PRIMARY KEY (id),
    -- The poller reports a targetExternalId; this is what resolves it.
    CONSTRAINT watch_target_source_key UNIQUE (tenant_id, source, external_id)
);

-- Poller/matcher lookup of what to watch for a given source.
CREATE INDEX IF NOT EXISTS idx_watch_target_source_active
    ON watch_target (tenant_id, source, active);

-- A member's standing interest in a target.
CREATE TABLE IF NOT EXISTS watch (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    member_id character varying(36) NOT NULL,
    target_id character varying(36) NOT NULL,
    criteria jsonb,
    channels jsonb,
    status character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT watch_pkey PRIMARY KEY (id),
    CONSTRAINT watch_status_check
        CHECK (status IN ('ACTIVE', 'PAUSED', 'EXPIRED', 'FULFILLED')),
    -- NO ACTION, not CASCADE: deleting a target that members are actively
    -- watching should fail loudly rather than silently discard their watches.
    CONSTRAINT watch_target_id_fkey FOREIGN KEY (target_id)
        REFERENCES watch_target (id)
);

-- The matcher's hot path: given a target that just opened, which watches care?
CREATE INDEX IF NOT EXISTS idx_watch_target_status
    ON watch (tenant_id, target_id, status);
-- The entitlement quota hook counts a member's watches on every create.
CREATE INDEX IF NOT EXISTS idx_watch_member_status
    ON watch (tenant_id, member_id, status);
-- Sweep for pass-scoped watches whose window has closed.
CREATE INDEX IF NOT EXISTS idx_watch_expiry
    ON watch (status, expires_at) WHERE expires_at IS NOT NULL;

-- Current known availability per (target, slot). One row per slot, updated in
-- place — this is state, not history.
--
-- episode_id is minted fresh on every CLOSED -> OPEN transition and is what makes
-- alerting idempotent: a slot that stays open across many polls keeps the same
-- episode, so a member is alerted once per genuine opening rather than once per
-- poll. A slot that closes and reopens is a NEW episode and alerts again.
CREATE TABLE IF NOT EXISTS availability_state (
    tenant_id character varying(36) NOT NULL,
    target_id character varying(36) NOT NULL,
    slot_key character varying(200) NOT NULL,
    status character varying(20) NOT NULL,
    episode_id character varying(36),
    window_start timestamp with time zone,
    window_end timestamp with time zone,
    quantity integer,
    last_seen_at timestamp with time zone DEFAULT now() NOT NULL,
    last_change_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT availability_state_pkey PRIMARY KEY (tenant_id, target_id, slot_key),
    CONSTRAINT availability_state_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

-- Matcher reads all open slots for a target it just received an event for.
CREATE INDEX IF NOT EXISTS idx_availability_state_open
    ON availability_state (tenant_id, target_id) WHERE status = 'OPEN';

-- Dedupe ledger: one row per (watch, slot, episode) actually alerted on.
CREATE TABLE IF NOT EXISTS alert (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    watch_id character varying(36) NOT NULL,
    target_id character varying(36) NOT NULL,
    slot_key character varying(200) NOT NULL,
    episode_id character varying(36) NOT NULL,
    window_start timestamp with time zone,
    window_end timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT alert_pkey PRIMARY KEY (id),
    -- The whole anti-spam guarantee lives here: at most one alert per member per
    -- slot per opening, enforced by the database rather than by matcher logic.
    CONSTRAINT alert_dedupe_key UNIQUE (tenant_id, watch_id, slot_key, episode_id)
);

-- Suppression window: "did we already alert this watch about this slot recently?"
CREATE INDEX IF NOT EXISTS idx_alert_suppression
    ON alert (tenant_id, watch_id, slot_key, created_at);

-- Per-channel delivery outcome for an alert.
CREATE TABLE IF NOT EXISTS alert_delivery (
    id character varying(36) NOT NULL,
    alert_id character varying(36) NOT NULL,
    channel character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'PENDING' NOT NULL,
    sent_at timestamp with time zone,
    error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT alert_delivery_pkey PRIMARY KEY (id),
    CONSTRAINT alert_delivery_channel_check
        CHECK (channel IN ('push', 'email', 'sms')),
    CONSTRAINT alert_delivery_status_check
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    -- Deliveries are meaningless without their alert, so they follow it.
    CONSTRAINT alert_delivery_alert_id_fkey FOREIGN KEY (alert_id)
        REFERENCES alert (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_alert_delivery_alert ON alert_delivery (alert_id);

ALTER TABLE watch_target ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY watch_target FORCE ROW LEVEL SECURITY;
ALTER TABLE watch ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY watch FORCE ROW LEVEL SECURITY;
ALTER TABLE availability_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY availability_state FORCE ROW LEVEL SECURITY;
ALTER TABLE alert ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY alert FORCE ROW LEVEL SECURITY;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['watch_target', 'watch', 'availability_state', 'alert']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_policies
                       WHERE tablename = t AND policyname = 'tenant_isolation') THEN
            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I '
                'USING (((tenant_id)::text = current_setting(''app.current_tenant_id''::text, true)))', t);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_policies
                       WHERE tablename = t AND policyname = 'admin_bypass') THEN
            EXECUTE format(
                'CREATE POLICY admin_bypass ON %I '
                'USING ((current_setting(''app.current_tenant_id''::text, true) = ''''::text))', t);
        END IF;
    END LOOP;
END $$;

-- alert_delivery has no tenant_id of its own: it is reachable only through its
-- alert, which is tenant-scoped and RLS'd, and the FK cascade ties their
-- lifetimes together. Adding a denormalized tenant_id purely to attach a policy
-- would create a second source of truth that could drift from the parent.
