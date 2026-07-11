-- Telehealth slice 7: archival & retention (specs/telehealth/7-archival-retention.md).
-- One immutable row per archived source (a CLOSED chat conversation or an ENDED
-- video session). The artifact (canonical JSON transcript + PDF render) lives in
-- S3 as file_attachment rows OWNED by the archive row (re-parented on archive so
-- a later live-message purge cannot delete what the transcript references).
-- Archive-then-purge, never delete-first; the retention sweep skips legal_hold.

CREATE TABLE IF NOT EXISTS telehealth_archive (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id character varying(36) NOT NULL,
    appointment_id character varying(36),
    portal_user_id character varying(36),
    artifact_attachment_ids jsonb,
    sha256 character varying(64),
    archived_at timestamp with time zone,
    archived_by character varying(64),
    retention_until timestamp with time zone,
    legal_hold boolean DEFAULT false NOT NULL,
    purged_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT telehealth_archive_pkey PRIMARY KEY (id),
    CONSTRAINT telehealth_archive_source_type_check
        CHECK (source_type IN ('CONVERSATION', 'VIDEO_SESSION'))
);

-- Idempotency: at most one archive per source (per tenant).
CREATE UNIQUE INDEX IF NOT EXISTS uq_telehealth_archive_source
    ON telehealth_archive (tenant_id, source_type, source_id);
-- Portal user reads own history.
CREATE INDEX IF NOT EXISTS idx_telehealth_archive_portal
    ON telehealth_archive (tenant_id, portal_user_id);
-- Retention purge sweep: due, not on legal hold, not already purged.
CREATE INDEX IF NOT EXISTS idx_telehealth_archive_retention
    ON telehealth_archive (tenant_id, retention_until)
    WHERE purged_at IS NULL AND NOT legal_hold;

ALTER TABLE telehealth_archive ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY telehealth_archive FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'telehealth_archive' AND policyname = 'tenant_isolation') THEN
        CREATE POLICY tenant_isolation ON telehealth_archive
            USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'telehealth_archive' AND policyname = 'admin_bypass') THEN
        CREATE POLICY admin_bypass ON telehealth_archive
            USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));
    END IF;
END $$;
