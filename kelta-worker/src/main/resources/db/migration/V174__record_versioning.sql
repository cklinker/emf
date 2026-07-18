-- Collection-level record versioning: a collection.track_history toggle and a
-- record_version table holding one full-record snapshot per create/update/delete.
-- Mirrors the field_history table shape (public schema, tenant_id + RLS).

ALTER TABLE collection ADD COLUMN track_history boolean DEFAULT false NOT NULL;

COMMENT ON COLUMN collection.track_history IS 'When true, every create/update/delete of a record in this collection writes a full snapshot row to record_version (supersedes per-field field.track_history for this collection).';

CREATE TABLE record_version (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    record_id character varying(36) NOT NULL,
    version_number integer NOT NULL,
    change_type character varying(10) NOT NULL,
    snapshot jsonb NOT NULL,
    changed_fields jsonb DEFAULT '[]'::jsonb NOT NULL,
    changed_by character varying(36) NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    change_source character varying(20) DEFAULT 'UI'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY record_version
    ADD CONSTRAINT record_version_pkey PRIMARY KEY (id);

ALTER TABLE ONLY record_version
    ADD CONSTRAINT uq_record_version UNIQUE (tenant_id, collection_id, record_id, version_number);

ALTER TABLE ONLY record_version
    ADD CONSTRAINT record_version_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);

ALTER TABLE ONLY record_version
    ADD CONSTRAINT record_version_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);

CREATE INDEX idx_record_version_record ON record_version USING btree (tenant_id, collection_id, record_id, version_number DESC);

COMMENT ON TABLE record_version IS 'Immutable full-record snapshots for collections with track_history enabled. One row per create/update/delete; version_number is 1-based per record.';
COMMENT ON COLUMN record_version.change_type IS 'CREATED, UPDATED, or DELETED';
COMMENT ON COLUMN record_version.snapshot IS 'Full field map of the record as of this version (post-change; for DELETED, the last state before deletion).';
COMMENT ON COLUMN record_version.changed_fields IS 'JSON array of field names changed in this version. CREATED = all non-null fields; DELETED = empty.';
COMMENT ON COLUMN record_version.change_source IS 'Source of the change: UI, API, WORKFLOW, SYSTEM, IMPORT';

ALTER TABLE record_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY record_version FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON record_version USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

CREATE POLICY admin_bypass ON record_version USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));
