-- Consumer-alerting slice 8: analytics capture
-- (specs/consumer-alerting/8-analytics-capture.md). Durable, tenant-scoped storage of the
-- questions a consumer product's users ask (search queries, assistant questions) plus
-- lightweight usage/acquisition events. This is the corpus behind the demand-mining loop and
-- the later Q&A/SEO page generation and demand clustering.
--
-- Backed by a READ-ONLY system collection (analytics-events): admins get JSON:API read for
-- free, but the generic route never writes it. Rows are inserted only by the capture paths
-- (SearchController auto-capture + the authenticated /api/analytics/events ingest) via direct
-- JDBC under the request tenant context.
--
-- Expected to be the highest-volume system table after record_version, so age-based retention
-- (AnalyticsRetentionSweep) ships in the same slice — "apply the flow-log retention lessons
-- from day one".

CREATE TABLE IF NOT EXISTS analytics_event (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    -- Open vocabulary (SEARCH_QUERY | ASSISTANT_QUESTION | PAGE_VIEW | WATCH_LIFECYCLE |
    -- CUSTOM). Deliberately NO CHECK constraint: a new client event kind must not require a
    -- migration.
    event_type character varying(30) NOT NULL,
    -- Verbatim question/search text; null for non-question events.
    query text,
    -- Search returned nothing: the highest-value signal (unmet demand).
    zero_result boolean,
    -- Best-effort resolved watch_target id. NO FK: analytics history outlives the target, and
    -- a deleted target must not orphan-block or cascade-delete demand data.
    matched_target_id character varying(36),
    path character varying(500),
    referrer character varying(500),
    utm jsonb,
    -- Coarse session correlation; client-supplied opaque id, never a device fingerprint.
    session_id character varying(64),
    -- Portal/staff user; null for later anonymous rows. NO FK (see matched_target_id).
    member_id character varying(36),
    -- Coarse geo only — no city, no precise coordinates.
    geo_country character varying(2),
    geo_region character varying(80),
    metadata jsonb,
    -- Event time; the client may supply it for batched offline events (clamped not-future
    -- server-side), else defaults to insert time.
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT analytics_event_pkey PRIMARY KEY (id)
);

-- The demand queries: "what SEARCH_QUERY/ASSISTANT_QUESTION events, newest first".
CREATE INDEX IF NOT EXISTS idx_analytics_event_type_time
    ON analytics_event (tenant_id, event_type, occurred_at);
-- The retention sweep's age scan.
CREATE INDEX IF NOT EXISTS idx_analytics_event_age
    ON analytics_event (tenant_id, occurred_at);

ALTER TABLE analytics_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY analytics_event FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'analytics_event' AND policyname = 'tenant_isolation') THEN
        EXECUTE
            'CREATE POLICY tenant_isolation ON analytics_event '
            'USING (((tenant_id)::text = current_setting(''app.current_tenant_id''::text, true)))';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'analytics_event' AND policyname = 'admin_bypass') THEN
        EXECUTE
            'CREATE POLICY admin_bypass ON analytics_event '
            'USING ((current_setting(''app.current_tenant_id''::text, true) = ''''::text))';
    END IF;
END $$;
