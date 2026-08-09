-- Consumer-alerting slice 11: SEO page generation
-- (specs/consumer-alerting/11-seo-page-generation.md). A nightly sweep computes a per-target
-- stat block into seo_page; a static content site renders these at build time via a service
-- token. The corpus for the SEO/content engine.
--
-- Backed by a READ-ONLY system collection (seo-pages). AGGREGATE ONLY — a row carries the
-- target's public metadata + aggregate counts, never member data, so it is safe to read with a
-- build-time service token. There is still NO anonymous bulk API.

CREATE TABLE IF NOT EXISTS seo_page (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    -- The watch_target this page is about. NO FK: a target may be deleted, and a stale page is
    -- pruned/unpublished by the sweep rather than cascade-removed.
    target_id character varying(36),
    -- URL slug, unique per tenant — human-readable, derived from the target name.
    slug character varying(200) NOT NULL,
    title character varying(255) NOT NULL,
    category character varying(50),
    -- Aggregate demand/success signals.
    watcher_count integer,
    win_count integer,
    last_win_at timestamp with time zone,
    -- Extensible stat block (odds, history summaries, ...).
    stats jsonb,
    -- §6.3 quality guardrail: only pages backed by enough real data are published; the rest stay
    -- unpublished (noindex) rather than becoming thin programmatic spam.
    published boolean DEFAULT false NOT NULL,
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT seo_page_pkey PRIMARY KEY (id),
    -- One page per slug per tenant — the upsert key.
    CONSTRAINT seo_page_slug_key UNIQUE (tenant_id, slug)
);

-- The build query: "published pages for this tenant".
CREATE INDEX IF NOT EXISTS idx_seo_page_published
    ON seo_page (tenant_id, published);
-- The sweep re-resolves a target's page.
CREATE INDEX IF NOT EXISTS idx_seo_page_target
    ON seo_page (tenant_id, target_id);

ALTER TABLE seo_page ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY seo_page FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'seo_page' AND policyname = 'tenant_isolation') THEN
        EXECUTE
            'CREATE POLICY tenant_isolation ON seo_page '
            'USING (((tenant_id)::text = current_setting(''app.current_tenant_id''::text, true)))';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'seo_page' AND policyname = 'admin_bypass') THEN
        EXECUTE
            'CREATE POLICY admin_bypass ON seo_page '
            'USING ((current_setting(''app.current_tenant_id''::text, true) = ''''::text))';
    END IF;
END $$;
