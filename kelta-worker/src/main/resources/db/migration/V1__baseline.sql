-- =============================================================================
-- V1__baseline.sql — consolidated Flyway baseline (flatten of V1..V161)
--
-- Generated 2026-07-06 from a fresh migrate of the V1..V161 chain (demo seed
-- V50 excluded), captured with pg_dump and made Flyway/JDBC-safe:
--   * psql "\" meta-commands + session SETs stripped
--   * schema-RELATIVE (no public. qualifier) so it honors Flyway currentSchema
--     — lands in `public` for prod, `ci_<tag>` for the integration harness,
--     matching the original migrations
--   * pg_trgm extension created up front (pg_dump --schema omits it)
--
-- Verified with Flyway 11.14.1: fresh-install parity vs the incremental chain
-- (105 tables, 132 RLS policies, identical bootstrap seed), plus 10 FK/audit
-- indexes that lived on production but were never in a migration (codified
-- below, IF NOT EXISTS). Applies cleanly into an isolated non-public schema.
--
-- Scope: the kelta-worker control-plane `public` schema ONLY. kelta-ai owns the
-- `ai_*` tables via its own Flyway history; per-tenant business-data schemas are
-- created at runtime; system-collection metadata is seeded at worker startup by
-- SystemCollectionSeeder (so it is not baked in here). placeholder-replacement
-- stays disabled (email templates store literal ${...}).
--
-- Superseded V1..V161 scripts are retained under db/migration-archive/.
-- Existing (already-migrated) databases reconcile via a one-time schema-history
-- cleanup — see README.md. New migrations continue at V162.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

--
-- PostgreSQL database dump
--


-- Dumped from database version 15.18 (Debian 15.18-1.pgdg12+1)
-- Dumped by pg_dump version 18.1


--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: search_index_update_vector(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION search_index_update_vector() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.search_content, ''));
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;




--
-- Name: api_call_idempotency; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE api_call_idempotency (
    tenant_id character varying(36) NOT NULL,
    idempotency_key character varying(200) NOT NULL,
    flow_run_id character varying(36),
    state_name character varying(200),
    status_code integer,
    response_body text,
    response_hash character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL
);

ALTER TABLE ONLY api_call_idempotency FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE api_call_idempotency; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE api_call_idempotency IS 'Cached responses for non-idempotent CALL_API steps; replayed on retry within TTL';


--
-- Name: api_operation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE api_operation (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    spec_id character varying(36) NOT NULL,
    operation_id character varying(200),
    synthetic_op_id character varying(200) NOT NULL,
    http_method character varying(10) NOT NULL,
    path_template character varying(1000) NOT NULL,
    summary character varying(500),
    description text,
    tags jsonb,
    parameters_schema jsonb,
    request_body_schema jsonb,
    response_schemas jsonb,
    security_required jsonb,
    deprecated boolean DEFAULT false NOT NULL,
    search_text text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY api_operation FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN api_operation.synthetic_op_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN api_operation.synthetic_op_id IS 'Always present; synthesized as <METHOD>_<sanitized_path> when the spec omits operationId';


--
-- Name: COLUMN api_operation.search_text; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN api_operation.search_text IS 'Concatenation of method + path + summary + tags for trigram search';


--
-- Name: api_spec; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE api_spec (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(1000),
    spec_version character varying(20) NOT NULL,
    api_title character varying(500),
    api_version character varying(50),
    base_url character varying(1000),
    servers jsonb,
    security_schemes jsonb,
    source_type character varying(20) NOT NULL,
    source_url character varying(2000),
    raw_spec text NOT NULL,
    raw_format character varying(10) NOT NULL,
    parsed_spec jsonb NOT NULL,
    spec_hash character varying(64) NOT NULL,
    revision integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    last_imported_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(36),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY api_spec FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN api_spec.spec_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN api_spec.spec_version IS 'OpenAPI version (e.g. 3.0.3, 3.1.0)';


--
-- Name: COLUMN api_spec.source_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN api_spec.source_type IS 'INLINE_JSON | INLINE_YAML | URL';


--
-- Name: COLUMN api_spec.raw_format; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN api_spec.raw_format IS 'json | yaml';


--
-- Name: COLUMN api_spec.spec_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN api_spec.spec_hash IS 'sha-256 of raw_spec — used to detect re-imports';


--
-- Name: approval_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE approval_instance (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    approval_process_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    record_id character varying(36) NOT NULL,
    submitted_by character varying(36) NOT NULL,
    current_step_number integer DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    submitted_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_approval_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'RECALLED'::character varying])::text[])))
);

ALTER TABLE ONLY approval_instance FORCE ROW LEVEL SECURITY;


--
-- Name: approval_process; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE approval_process (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(1000),
    active boolean DEFAULT true,
    entry_criteria text,
    record_editability character varying(20) DEFAULT 'LOCKED'::character varying,
    initial_submitter_field character varying(100),
    on_submit_field_updates jsonb DEFAULT '[]'::jsonb,
    on_approval_field_updates jsonb DEFAULT '[]'::jsonb,
    on_rejection_field_updates jsonb DEFAULT '[]'::jsonb,
    on_recall_field_updates jsonb DEFAULT '[]'::jsonb,
    allow_recall boolean DEFAULT true,
    execution_order integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY approval_process FORCE ROW LEVEL SECURITY;


--
-- Name: approval_step; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE approval_step (
    id character varying(36) NOT NULL,
    approval_process_id character varying(36) NOT NULL,
    step_number integer NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    entry_criteria text,
    approver_type character varying(30) NOT NULL,
    approver_id character varying(36),
    approver_field character varying(100),
    unanimity_required boolean DEFAULT false,
    escalation_timeout_hours integer,
    escalation_action character varying(20),
    on_approve_action character varying(20) DEFAULT 'NEXT_STEP'::character varying,
    on_reject_action character varying(20) DEFAULT 'REJECT_FINAL'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: approval_step_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE approval_step_instance (
    id character varying(36) NOT NULL,
    approval_instance_id character varying(36) NOT NULL,
    step_id character varying(36) NOT NULL,
    assigned_to character varying(36) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    comments text,
    acted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_step_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'REASSIGNED'::character varying])::text[])))
);


--
-- Name: bulk_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE bulk_job (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    operation character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED'::character varying NOT NULL,
    total_records integer DEFAULT 0,
    processed_records integer DEFAULT 0,
    success_records integer DEFAULT 0,
    error_records integer DEFAULT 0,
    external_id_field character varying(100),
    content_type character varying(50) DEFAULT 'application/json'::character varying,
    batch_size integer DEFAULT 200,
    created_by character varying(36) NOT NULL,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    data_payload jsonb,
    file_storage_key character varying(500),
    error_message text,
    CONSTRAINT chk_bulk_operation CHECK (((operation)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'UPSERT'::character varying, 'DELETE'::character varying])::text[]))),
    CONSTRAINT chk_bulk_status CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'ABORTED'::character varying])::text[])))
);

ALTER TABLE ONLY bulk_job FORCE ROW LEVEL SECURITY;


--
-- Name: bulk_job_result; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE bulk_job_result (
    id character varying(36) NOT NULL,
    bulk_job_id character varying(36) NOT NULL,
    record_index integer NOT NULL,
    record_id character varying(36),
    status character varying(20) NOT NULL,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_result_status CHECK (((status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying])::text[])))
);


--
-- Name: collection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE collection (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    active boolean DEFAULT true NOT NULL,
    current_version integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone,
    display_name character varying(100),
    path character varying(255),
    tenant_id character varying(36) NOT NULL,
    system_collection boolean DEFAULT false,
    display_field_id character varying(36),
    created_by character varying(255),
    updated_by character varying(255),
    adapter_config jsonb
);

ALTER TABLE ONLY collection FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE collection; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE collection IS 'Stores collection definitions - logical groupings of data entities with defined fields and operations';


--
-- Name: collection_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE collection_version (
    id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    version integer NOT NULL,
    schema jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: TABLE collection_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE collection_version IS 'Stores immutable snapshots of collection schemas for versioning and historical tracking';


--
-- Name: connected_app; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE connected_app (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    client_id character varying(100) NOT NULL,
    client_secret_hash character varying(200) NOT NULL,
    redirect_uris jsonb DEFAULT '[]'::jsonb,
    scopes jsonb DEFAULT '["api"]'::jsonb,
    ip_restrictions jsonb DEFAULT '[]'::jsonb,
    rate_limit_per_hour integer DEFAULT 10000,
    active boolean DEFAULT true,
    last_used_at timestamp with time zone,
    created_by character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    grant_types jsonb DEFAULT '["client_credentials"]'::jsonb NOT NULL,
    require_pkce boolean DEFAULT false NOT NULL,
    consent_required boolean DEFAULT true NOT NULL
);

ALTER TABLE ONLY connected_app FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN connected_app.grant_types; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN connected_app.grant_types IS 'Allowed OAuth2 grant types: client_credentials, authorization_code';


--
-- Name: COLUMN connected_app.require_pkce; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN connected_app.require_pkce IS 'Whether PKCE is required (recommended for public/SPA clients)';


--
-- Name: COLUMN connected_app.consent_required; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN connected_app.consent_required IS 'Whether user consent screen is shown during authorization';


--
-- Name: connected_app_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE connected_app_audit (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    connected_app_id character varying(36) NOT NULL,
    action character varying(30) NOT NULL,
    details jsonb,
    performed_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: connected_app_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE connected_app_token (
    id character varying(36) NOT NULL,
    connected_app_id character varying(36) NOT NULL,
    token_hash character varying(200) NOT NULL,
    scopes jsonb NOT NULL,
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked boolean DEFAULT false,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: credential; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE credential (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    display_name character varying(200),
    description character varying(500),
    type character varying(50) NOT NULL,
    provider_template character varying(100),
    data_enc text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    last_test_at timestamp with time zone,
    last_test_status character varying(20),
    last_test_error text,
    active boolean DEFAULT true NOT NULL,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(36),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY credential FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN credential.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN credential.type IS 'api_key | bearer_token | basic_auth | oauth2_client_credentials | oauth2_authorization_code | smtp | custom';


--
-- Name: COLUMN credential.data_enc; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN credential.data_enc IS 'AES-256-GCM encrypted JSON blob of secret fields (format: enc:v1:<iv>:<ct>)';


--
-- Name: COLUMN credential.metadata; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN credential.metadata IS 'Non-secret credential configuration (host, port, scopes, urls, etc.)';


--
-- Name: credential_oauth_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE credential_oauth_token (
    id character varying(36) NOT NULL,
    credential_id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    access_token_enc text NOT NULL,
    refresh_token_enc text,
    token_type character varying(40),
    expires_at timestamp with time zone NOT NULL,
    refreshed_at timestamp with time zone NOT NULL,
    refresh_failure_count integer DEFAULT 0 NOT NULL,
    last_refresh_error text,
    scope text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY credential_oauth_token FORCE ROW LEVEL SECURITY;


--
-- Name: dashboard; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dashboard (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(1000),
    folder_id character varying(36),
    access_level character varying(20) DEFAULT 'PRIVATE'::character varying,
    is_dynamic boolean DEFAULT false,
    running_user_id character varying(36),
    column_count integer DEFAULT 3,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255)
);

ALTER TABLE ONLY dashboard FORCE ROW LEVEL SECURITY;


--
-- Name: dashboard_component; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE dashboard_component (
    id character varying(36) NOT NULL,
    dashboard_id character varying(36) NOT NULL,
    report_id character varying(36) NOT NULL,
    component_type character varying(20) NOT NULL,
    title character varying(200),
    column_position integer NOT NULL,
    row_position integer NOT NULL,
    column_span integer DEFAULT 1,
    row_span integer DEFAULT 1,
    config jsonb DEFAULT '{}'::jsonb,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY dashboard_component FORCE ROW LEVEL SECURITY;


--
-- Name: data_export; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE data_export (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    export_scope character varying(20) DEFAULT 'SELECTIVE'::character varying NOT NULL,
    collection_ids text,
    format character varying(10) DEFAULT 'CSV'::character varying NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    total_records integer DEFAULT 0 NOT NULL,
    records_exported integer DEFAULT 0 NOT NULL,
    storage_key character varying(500),
    file_size_bytes bigint,
    created_by character varying(255),
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    error_message text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_export_format CHECK (((format)::text = ANY ((ARRAY['CSV'::character varying, 'JSON'::character varying])::text[]))),
    CONSTRAINT chk_export_scope CHECK (((export_scope)::text = ANY ((ARRAY['FULL'::character varying, 'SELECTIVE'::character varying])::text[]))),
    CONSTRAINT chk_export_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: delegated_admin_scope; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE delegated_admin_scope (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    active boolean DEFAULT true NOT NULL,
    delegated_user_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    manageable_profile_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    can_create_users boolean DEFAULT false NOT NULL,
    can_deactivate_users boolean DEFAULT false NOT NULL,
    can_reset_passwords boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    updated_by character varying(36)
);

ALTER TABLE ONLY delegated_admin_scope FORCE ROW LEVEL SECURITY;


--
-- Name: email_campaign; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE email_campaign (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    subject character varying(500) NOT NULL,
    body_html text,
    template_id character varying(36),
    target_collection character varying(100) NOT NULL,
    recipient_email_field character varying(100) NOT NULL,
    filter_json jsonb,
    list_view_id character varying(36),
    from_name character varying(200),
    from_address character varying(320),
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    scheduled_at timestamp with time zone,
    total_recipients integer DEFAULT 0 NOT NULL,
    sent_count integer DEFAULT 0 NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    open_count integer DEFAULT 0 NOT NULL,
    click_count integer DEFAULT 0 NOT NULL,
    unsubscribe_count integer DEFAULT 0 NOT NULL,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    CONSTRAINT chk_campaign_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SCHEDULED'::character varying, 'QUEUED'::character varying, 'SENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);

ALTER TABLE ONLY email_campaign FORCE ROW LEVEL SECURITY;


--
-- Name: email_campaign_recipient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE email_campaign_recipient (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    campaign_id character varying(36) NOT NULL,
    record_id character varying(36),
    email character varying(320) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    error_message text,
    email_log_id character varying(36),
    open_count integer DEFAULT 0 NOT NULL,
    click_count integer DEFAULT 0 NOT NULL,
    sent_at timestamp with time zone,
    opened_at timestamp with time zone,
    clicked_at timestamp with time zone,
    unsubscribed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    CONSTRAINT chk_recipient_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying, 'SUPPRESSED'::character varying])::text[])))
);

ALTER TABLE ONLY email_campaign_recipient FORCE ROW LEVEL SECURITY;


--
-- Name: email_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE email_log (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    template_id character varying(36),
    recipient_email character varying(320) NOT NULL,
    subject character varying(500) NOT NULL,
    status character varying(20) DEFAULT 'QUEUED'::character varying NOT NULL,
    source character varying(30),
    source_id character varying(36),
    error_message text,
    sent_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    smtp_host character varying(255)
);

ALTER TABLE ONLY email_log FORCE ROW LEVEL SECURITY;


--
-- Name: email_suppression; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE email_suppression (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    email character varying(320) NOT NULL,
    reason character varying(30) DEFAULT 'UNSUBSCRIBE'::character varying NOT NULL,
    campaign_id character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    updated_by character varying(36),
    CONSTRAINT chk_suppression_reason CHECK (((reason)::text = ANY ((ARRAY['UNSUBSCRIBE'::character varying, 'BOUNCE'::character varying, 'COMPLAINT'::character varying, 'MANUAL'::character varying])::text[])))
);

ALTER TABLE ONLY email_suppression FORCE ROW LEVEL SECURITY;


--
-- Name: email_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE email_template (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    subject character varying(500) NOT NULL,
    body_html text NOT NULL,
    body_text text,
    related_collection_id character varying(36),
    folder character varying(100),
    is_active boolean DEFAULT true,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    variables_schema jsonb,
    smtp_credential_id character varying(36),
    template_key character varying(100)
);

ALTER TABLE ONLY email_template FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN email_template.variables_schema; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN email_template.variables_schema IS 'JSON schema describing the inputs the template expects (drives the PayloadMapper target panel)';


--
-- Name: COLUMN email_template.smtp_credential_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN email_template.smtp_credential_id IS 'Optional: route this template through a specific SMTP credential from the vault (PR 1)';


--
-- Name: COLUMN email_template.template_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN email_template.template_key IS 'Stable identifier used by lifecycle code to look up a template (e.g. user.invite). NULL for ad-hoc workflow-only templates. Unique per tenant when set.';


--
-- Name: environment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE environment (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    type character varying(20) DEFAULT 'SANDBOX'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    source_env_id character varying(36),
    config jsonb,
    created_by character varying(255),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    sandbox_tenant_id character varying(36),
    remote_base_url character varying(500),
    remote_tenant_slug character varying(63),
    credential_ref character varying(200),
    CONSTRAINT chk_env_locality CHECK ((((type)::text = 'PRODUCTION'::text) OR (NOT ((sandbox_tenant_id IS NOT NULL) AND (remote_base_url IS NOT NULL))))),
    CONSTRAINT chk_env_status CHECK (((status)::text = ANY ((ARRAY['CREATING'::character varying, 'ACTIVE'::character varying, 'REFRESHING'::character varying, 'ARCHIVED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT chk_env_type CHECK (((type)::text = ANY ((ARRAY['PRODUCTION'::character varying, 'SANDBOX'::character varying, 'STAGING'::character varying])::text[])))
);

ALTER TABLE ONLY environment FORCE ROW LEVEL SECURITY;


--
-- Name: environment_promotion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE environment_promotion (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    source_env_id character varying(36) NOT NULL,
    target_env_id character varying(36) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    promotion_type character varying(20) DEFAULT 'FULL'::character varying NOT NULL,
    snapshot_id character varying(36),
    changes_summary jsonb,
    items_promoted integer DEFAULT 0 NOT NULL,
    items_skipped integer DEFAULT 0 NOT NULL,
    items_failed integer DEFAULT 0 NOT NULL,
    error_message text,
    approved_by character varying(255),
    approved_at timestamp without time zone,
    promoted_by character varying(255),
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    conflict_mode character varying(10) DEFAULT 'SKIP'::character varying NOT NULL,
    target_snapshot_id character varying(36),
    CONSTRAINT chk_promo_conflict CHECK (((conflict_mode)::text = ANY ((ARRAY['SKIP'::character varying, 'OVERWRITE'::character varying])::text[]))),
    CONSTRAINT chk_promo_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'ROLLED_BACK'::character varying])::text[]))),
    CONSTRAINT chk_promo_type CHECK (((promotion_type)::text = ANY ((ARRAY['FULL'::character varying, 'SELECTIVE'::character varying])::text[])))
);

ALTER TABLE ONLY environment_promotion FORCE ROW LEVEL SECURITY;


--
-- Name: field; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE field (
    id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(50) NOT NULL,
    required boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    constraints jsonb,
    description character varying(500),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone,
    display_name character varying(100),
    unique_constraint boolean DEFAULT false,
    indexed boolean DEFAULT false,
    default_value jsonb,
    reference_target character varying(100),
    field_order integer DEFAULT 0,
    field_type_config jsonb,
    auto_number_sequence_name character varying(100),
    relationship_type character varying(20),
    relationship_name character varying(100),
    cascade_delete boolean DEFAULT false NOT NULL,
    reference_collection_id character varying(36),
    track_history boolean DEFAULT false,
    column_name character varying(100),
    immutable boolean DEFAULT false NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    searchable boolean DEFAULT false
);


--
-- Name: TABLE field; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE field IS 'Stores field definitions within collections - typed attributes with optional constraints';


--
-- Name: COLUMN field.field_type_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN field.field_type_config IS 'Type-specific configuration. Schema varies by field type:
  PICKLIST: {"globalPicklistId": "uuid", "restricted": true, "sorted": false}
  MULTI_PICKLIST: {"globalPicklistId": "uuid", "restricted": true, "sorted": false}
  AUTO_NUMBER: {"prefix": "TICKET-", "padding": 4, "startValue": 1}
  CURRENCY: {"precision": 2, "defaultCurrencyCode": "USD"}
  FORMULA: {"expression": "Amount * Quantity", "returnType": "DOUBLE"}
  ROLLUP_SUMMARY: {"childCollection": "line_items", "aggregateFunction": "SUM", "aggregateField": "amount", "filter": {}}
  ENCRYPTED: {"algorithm": "AES-256-GCM"}
  GEOLOCATION: {"format": "DECIMAL_DEGREES"}
';


--
-- Name: COLUMN field.track_history; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN field.track_history IS 'When true, changes to this field are recorded in the field_history table.
 Default: false. Enable for fields that need audit trails.';


--
-- Name: COLUMN field.searchable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN field.searchable IS 'When true, this field''s values are included in the full-text search index.
 The collection display field is always searchable regardless of this flag.';


--
-- Name: field_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE field_history (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    record_id character varying(36) NOT NULL,
    field_name character varying(100) NOT NULL,
    old_value jsonb,
    new_value jsonb,
    changed_by character varying(36) NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    change_source character varying(20) DEFAULT 'UI'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY field_history FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN field_history.change_source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN field_history.change_source IS 'Source of the change: UI, API, WORKFLOW, SYSTEM, IMPORT';


--
-- Name: field_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE field_version (
    id character varying(36) NOT NULL,
    collection_version_id character varying(36) NOT NULL,
    field_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(50) NOT NULL,
    required boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    constraints jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: TABLE field_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE field_version IS 'Stores snapshots of field definitions at specific collection versions';


--
-- Name: file_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE file_attachment (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    record_id character varying(36) NOT NULL,
    file_name character varying(500) NOT NULL,
    file_size bigint DEFAULT 0 NOT NULL,
    content_type character varying(200) NOT NULL,
    storage_key character varying(500),
    uploaded_by character varying(320) NOT NULL,
    uploaded_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY file_attachment FORCE ROW LEVEL SECURITY;


--
-- Name: flow; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE flow (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(1000),
    flow_type character varying(30) NOT NULL,
    active boolean DEFAULT false,
    version integer DEFAULT 1,
    trigger_config jsonb,
    definition jsonb NOT NULL,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    last_scheduled_run timestamp with time zone,
    published_version integer,
    CONSTRAINT chk_flow_type CHECK (((flow_type)::text = ANY ((ARRAY['RECORD_TRIGGERED'::character varying, 'NATS_TRIGGERED'::character varying, 'SCHEDULED'::character varying, 'AUTOLAUNCHED'::character varying, 'SCREEN'::character varying])::text[])))
);

ALTER TABLE ONLY flow FORCE ROW LEVEL SECURITY;


--
-- Name: flow_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE flow_audit_log (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    flow_id character varying(36) NOT NULL,
    action character varying(30) NOT NULL,
    user_id character varying(36),
    details jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: flow_execution; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE flow_execution (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    flow_id character varying(36) NOT NULL,
    status character varying(20) DEFAULT 'RUNNING'::character varying NOT NULL,
    started_by character varying(36),
    trigger_record_id character varying(36),
    variables jsonb DEFAULT '{}'::jsonb,
    current_node_id character varying(100),
    error_message text,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    state_data jsonb DEFAULT '{}'::jsonb,
    step_count integer DEFAULT 0,
    duration_ms integer,
    initial_input jsonb,
    is_test boolean DEFAULT false,
    flow_version integer,
    CONSTRAINT chk_flow_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'WAITING'::character varying, 'CANCELLED'::character varying])::text[])))
);

ALTER TABLE ONLY flow_execution FORCE ROW LEVEL SECURITY;


--
-- Name: flow_pending_resume; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE flow_pending_resume (
    id character varying(36) NOT NULL,
    execution_id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    resume_at timestamp with time zone,
    resume_event character varying(200),
    claimed_by character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: flow_step_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE flow_step_log (
    id character varying(36) NOT NULL,
    execution_id character varying(36) NOT NULL,
    state_id character varying(100) NOT NULL,
    state_name character varying(200),
    state_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    input_snapshot jsonb,
    output_snapshot jsonb,
    error_message text,
    error_code character varying(100),
    attempt_number integer DEFAULT 1,
    duration_ms integer,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    parent_execution_id character varying(36),
    branch_index integer
);


--
-- Name: flow_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE flow_version (
    id character varying(36) NOT NULL,
    flow_id character varying(36) NOT NULL,
    version_number integer NOT NULL,
    definition jsonb NOT NULL,
    change_summary character varying(500),
    created_by character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: global_picklist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE global_picklist (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    sorted boolean DEFAULT false,
    restricted boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY global_picklist FORCE ROW LEVEL SECURITY;


--
-- Name: group_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE group_membership (
    id character varying(36) NOT NULL,
    group_id character varying(36) NOT NULL,
    member_type character varying(10) NOT NULL,
    member_id character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL,
    CONSTRAINT chk_member_type CHECK (((member_type)::text = ANY ((ARRAY['USER'::character varying, 'GROUP'::character varying])::text[])))
);

ALTER TABLE ONLY group_membership FORCE ROW LEVEL SECURITY;


--
-- Name: job_execution_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE job_execution_log (
    id character varying(36) NOT NULL,
    job_id character varying(36) NOT NULL,
    status character varying(20) NOT NULL,
    records_processed integer DEFAULT 0,
    error_message text,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    duration_ms integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: layout_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE layout_assignment (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    record_type_id character varying(36),
    layout_id character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    condition jsonb,
    evaluation_order integer DEFAULT 100 NOT NULL
);


--
-- Name: layout_field; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE layout_field (
    id character varying(36) NOT NULL,
    section_id character varying(36) NOT NULL,
    field_id character varying(36) NOT NULL,
    column_number integer DEFAULT 1,
    sort_order integer NOT NULL,
    is_required_on_layout boolean DEFAULT false,
    is_read_only_on_layout boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    label_override character varying(200),
    help_text_override character varying(500),
    visibility_rule jsonb,
    column_span integer DEFAULT 1,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY layout_field FORCE ROW LEVEL SECURITY;


--
-- Name: layout_related_list; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE layout_related_list (
    id character varying(36) NOT NULL,
    layout_id character varying(36) NOT NULL,
    related_collection_id character varying(36) NOT NULL,
    relationship_field_id character varying(36) NOT NULL,
    display_columns jsonb NOT NULL,
    sort_field character varying(100),
    sort_direction character varying(4) DEFAULT 'DESC'::character varying,
    row_limit integer DEFAULT 10,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY layout_related_list FORCE ROW LEVEL SECURITY;


--
-- Name: layout_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE layout_rule (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    layout_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    kind character varying(20) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    when_events jsonb NOT NULL,
    target_field character varying(100),
    depends_on jsonb,
    body jsonb NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_layout_rule_kind CHECK (((kind)::text = ANY ((ARRAY['COMPUTE'::character varying, 'VALIDATE'::character varying, 'DEFAULT'::character varying, 'TRANSFORM'::character varying])::text[])))
);

ALTER TABLE ONLY layout_rule FORCE ROW LEVEL SECURITY;


--
-- Name: layout_section; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE layout_section (
    id character varying(36) NOT NULL,
    layout_id character varying(36) NOT NULL,
    heading character varying(200),
    columns integer DEFAULT 2,
    sort_order integer NOT NULL,
    collapsed boolean DEFAULT false,
    style character varying(20) DEFAULT 'DEFAULT'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    section_type character varying(30) DEFAULT 'STANDARD'::character varying,
    tab_group character varying(100),
    tab_label character varying(200),
    visibility_rule jsonb,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY layout_section FORCE ROW LEVEL SECURITY;


--
-- Name: list_view; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE list_view (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    created_by character varying(36),
    visibility character varying(20) DEFAULT 'PRIVATE'::character varying,
    is_default boolean DEFAULT false,
    columns jsonb NOT NULL,
    filter_logic character varying(500),
    filters jsonb DEFAULT '[]'::jsonb NOT NULL,
    sort_field character varying(100),
    sort_direction character varying(4) DEFAULT 'ASC'::character varying,
    row_limit integer DEFAULT 50,
    chart_config jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    sort jsonb
);

ALTER TABLE ONLY list_view FORCE ROW LEVEL SECURITY;


--
-- Name: login_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE login_history (
    id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    login_time timestamp with time zone DEFAULT now() NOT NULL,
    source_ip character varying(45),
    login_type character varying(20) DEFAULT 'UI'::character varying NOT NULL,
    status character varying(20) NOT NULL,
    user_agent character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_login_status CHECK (((status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILED'::character varying, 'LOCKED_OUT'::character varying])::text[]))),
    CONSTRAINT chk_login_type CHECK (((login_type)::text = ANY ((ARRAY['UI'::character varying, 'API'::character varying, 'OAUTH'::character varying, 'SERVICE_ACCOUNT'::character varying])::text[])))
);

ALTER TABLE ONLY login_history FORCE ROW LEVEL SECURITY;


--
-- Name: metadata_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE metadata_snapshot (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    environment_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    snapshot_data jsonb NOT NULL,
    item_count integer DEFAULT 0 NOT NULL,
    created_by character varying(255),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY metadata_snapshot FORCE ROW LEVEL SECURITY;


--
-- Name: migration_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE migration_run (
    id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    from_version integer NOT NULL,
    to_version integer NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    error_message character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone,
    tenant_id character varying(36) NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_migration_run_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'ROLLED_BACK'::character varying])::text[]))),
    CONSTRAINT chk_migration_run_versions CHECK (((from_version < to_version) OR (from_version > to_version)))
);

ALTER TABLE ONLY migration_run FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE migration_run; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE migration_run IS 'Stores migration execution records tracking schema changes';


--
-- Name: migration_step; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE migration_step (
    id character varying(36) NOT NULL,
    migration_run_id character varying(36) NOT NULL,
    step_number integer NOT NULL,
    operation character varying(100) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    details jsonb,
    error_message character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_migration_step_number CHECK ((step_number > 0)),
    CONSTRAINT chk_migration_step_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[])))
);


--
-- Name: TABLE migration_step; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE migration_step IS 'Stores individual steps within migration runs with status tracking';


--
-- Name: note; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE note (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    record_id character varying(36) NOT NULL,
    content text NOT NULL,
    created_by character varying(320) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255)
);

ALTER TABLE ONLY note FORCE ROW LEVEL SECURITY;


--
-- Name: oauth2_authorization; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE oauth2_authorization (
    id character varying(100) NOT NULL,
    registered_client_id character varying(100) NOT NULL,
    principal_name character varying(200) NOT NULL,
    authorization_grant_type character varying(100) NOT NULL,
    authorized_scopes character varying(1000),
    attributes text,
    state character varying(500),
    authorization_code_value text,
    authorization_code_issued_at timestamp with time zone,
    authorization_code_expires_at timestamp with time zone,
    authorization_code_metadata text,
    access_token_value text,
    access_token_issued_at timestamp with time zone,
    access_token_expires_at timestamp with time zone,
    access_token_metadata text,
    access_token_type character varying(100),
    access_token_scopes character varying(1000),
    oidc_id_token_value text,
    oidc_id_token_issued_at timestamp with time zone,
    oidc_id_token_expires_at timestamp with time zone,
    oidc_id_token_metadata text,
    oidc_id_token_claims text,
    refresh_token_value text,
    refresh_token_issued_at timestamp with time zone,
    refresh_token_expires_at timestamp with time zone,
    refresh_token_metadata text,
    user_code_value text,
    user_code_issued_at timestamp with time zone,
    user_code_expires_at timestamp with time zone,
    user_code_metadata text,
    device_code_value text,
    device_code_issued_at timestamp with time zone,
    device_code_expires_at timestamp with time zone,
    device_code_metadata text
);


--
-- Name: oauth2_authorization_consent; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE oauth2_authorization_consent (
    registered_client_id character varying(100) NOT NULL,
    principal_name character varying(200) NOT NULL,
    authorities character varying(1000) NOT NULL
);


--
-- Name: oauth2_registered_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE oauth2_registered_client (
    id character varying(100) NOT NULL,
    client_id character varying(100) NOT NULL,
    client_id_issued_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret character varying(200),
    client_secret_expires_at timestamp with time zone,
    client_name character varying(200) NOT NULL,
    client_authentication_methods character varying(1000) NOT NULL,
    authorization_grant_types character varying(1000) NOT NULL,
    redirect_uris character varying(1000),
    post_logout_redirect_uris character varying(1000),
    scopes character varying(1000) NOT NULL,
    client_settings character varying(2000) NOT NULL,
    token_settings character varying(2000) NOT NULL
);


--
-- Name: observability_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE observability_settings (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    setting_key character varying(100) NOT NULL,
    setting_value character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: oidc_provider; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE oidc_provider (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    issuer character varying(500) NOT NULL,
    jwks_uri character varying(500) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    client_id character varying(200),
    audience character varying(200),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone,
    roles_claim character varying(200),
    roles_mapping text,
    email_claim character varying(200),
    username_claim character varying(200),
    name_claim character varying(200),
    tenant_id character varying(36) NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    groups_claim character varying(200),
    groups_profile_mapping text,
    client_secret_enc text,
    authorization_uri character varying(500),
    token_uri character varying(500),
    userinfo_uri character varying(500),
    end_session_uri character varying(500),
    discovery_status character varying(20) DEFAULT 'unknown'::character varying,
    is_internal boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_oidc_provider_groups_profile_mapping_json CHECK (((groups_profile_mapping IS NULL) OR ((groups_profile_mapping)::jsonb IS NOT NULL))),
    CONSTRAINT chk_oidc_provider_issuer CHECK (((issuer)::text ~ '^https?://'::text)),
    CONSTRAINT chk_oidc_provider_roles_mapping_json CHECK (((roles_mapping IS NULL) OR ((roles_mapping)::jsonb IS NOT NULL)))
);

ALTER TABLE ONLY oidc_provider FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE oidc_provider; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE oidc_provider IS 'Stores OpenID Connect identity provider configurations for JWT validation';


--
-- Name: COLUMN oidc_provider.roles_claim; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.roles_claim IS 'Path to roles claim in JWT token (e.g., roles, realm_access.roles, groups). Supports dot notation for nested claims.';


--
-- Name: COLUMN oidc_provider.roles_mapping; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.roles_mapping IS 'JSON mapping of external role values to internal role names. Example: {"external-admin": "ADMIN", "external-user": "USER"}';


--
-- Name: COLUMN oidc_provider.email_claim; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.email_claim IS 'Path to email claim in JWT token. Default: email';


--
-- Name: COLUMN oidc_provider.username_claim; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.username_claim IS 'Path to username claim in JWT token. Default: preferred_username';


--
-- Name: COLUMN oidc_provider.name_claim; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.name_claim IS 'Path to name/display name claim in JWT token. Default: name';


--
-- Name: COLUMN oidc_provider.client_secret_enc; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.client_secret_enc IS 'AES-256-GCM encrypted client secret for token exchange with external IdPs';


--
-- Name: COLUMN oidc_provider.authorization_uri; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.authorization_uri IS 'Override for OIDC authorization endpoint (auto-discovered from issuer when NULL)';


--
-- Name: COLUMN oidc_provider.token_uri; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.token_uri IS 'Override for OIDC token endpoint (auto-discovered from issuer when NULL)';


--
-- Name: COLUMN oidc_provider.userinfo_uri; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.userinfo_uri IS 'Override for OIDC userinfo endpoint (auto-discovered from issuer when NULL)';


--
-- Name: COLUMN oidc_provider.end_session_uri; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.end_session_uri IS 'Override for OIDC end session endpoint (auto-discovered from issuer when NULL)';


--
-- Name: COLUMN oidc_provider.discovery_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN oidc_provider.discovery_status IS 'OIDC Discovery status: discovered, manual, error, unknown';


--
-- Name: package; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE package (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    version character varying(50) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    tenant_id character varying(36) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY package FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE package; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE package IS 'Stores configuration packages for export/import and environment promotion';


--
-- Name: package_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE package_history (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    version character varying(50) NOT NULL,
    description text,
    type character varying(10) NOT NULL,
    status character varying(10) DEFAULT 'pending'::character varying NOT NULL,
    items text,
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_package_history_status CHECK (((status)::text = ANY ((ARRAY['success'::character varying, 'failed'::character varying, 'pending'::character varying])::text[]))),
    CONSTRAINT chk_package_history_type CHECK (((type)::text = ANY ((ARRAY['export'::character varying, 'import'::character varying])::text[])))
);


--
-- Name: package_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE package_item (
    id character varying(36) NOT NULL,
    package_id character varying(36) NOT NULL,
    item_type character varying(50) NOT NULL,
    item_id character varying(36) NOT NULL,
    content jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_package_item_type CHECK (((item_type)::text = ANY ((ARRAY['COLLECTION'::character varying, 'FIELD'::character varying, 'ROLE'::character varying, 'POLICY'::character varying, 'ROUTE_POLICY'::character varying, 'FIELD_POLICY'::character varying, 'OIDC_PROVIDER'::character varying, 'UI_PAGE'::character varying, 'UI_MENU'::character varying, 'UI_MENU_ITEM'::character varying])::text[])))
);


--
-- Name: TABLE package_item; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE package_item IS 'Stores individual items within configuration packages';


--
-- Name: page_layout; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE page_layout (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    layout_type character varying(20) DEFAULT 'DETAIL'::character varying,
    is_default boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    default_filter jsonb,
    default_sort_field character varying(100),
    default_sort_direction character varying(4) DEFAULT 'ASC'::character varying NOT NULL,
    default_row_limit integer DEFAULT 50 NOT NULL,
    header_config jsonb,
    rail_blocks jsonb
);

ALTER TABLE ONLY page_layout FORCE ROW LEVEL SECURITY;


--
-- Name: password_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE password_history (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    user_id character varying(36) NOT NULL,
    password_hash character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: password_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE password_policy (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    tenant_id character varying(36) NOT NULL,
    min_length integer DEFAULT 8 NOT NULL,
    max_length integer DEFAULT 128 NOT NULL,
    require_uppercase boolean DEFAULT false NOT NULL,
    require_lowercase boolean DEFAULT false NOT NULL,
    require_digit boolean DEFAULT false NOT NULL,
    require_special boolean DEFAULT false NOT NULL,
    history_count integer DEFAULT 3 NOT NULL,
    dictionary_check boolean DEFAULT true NOT NULL,
    personal_data_check boolean DEFAULT true NOT NULL,
    lockout_threshold integer DEFAULT 5 NOT NULL,
    lockout_duration_minutes integer DEFAULT 30 NOT NULL,
    max_age_days integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    mfa_required boolean DEFAULT false NOT NULL
);


--
-- Name: picklist_dependency; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE picklist_dependency (
    id character varying(36) NOT NULL,
    controlling_field_id character varying(36) NOT NULL,
    dependent_field_id character varying(36) NOT NULL,
    mapping jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY picklist_dependency FORCE ROW LEVEL SECURITY;


--
-- Name: picklist_value; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE picklist_value (
    id character varying(36) NOT NULL,
    picklist_source_type character varying(20) NOT NULL,
    picklist_source_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    is_default boolean DEFAULT false,
    is_active boolean DEFAULT true,
    sort_order integer DEFAULT 0,
    color character varying(20),
    description character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL,
    CONSTRAINT chk_source_type CHECK (((picklist_source_type)::text = ANY ((ARRAY['FIELD'::character varying, 'GLOBAL'::character varying])::text[])))
);

ALTER TABLE ONLY picklist_value FORCE ROW LEVEL SECURITY;


--
-- Name: platform_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE platform_instance (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: platform_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE platform_user (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    email character varying(320) NOT NULL,
    username character varying(100),
    first_name character varying(100),
    last_name character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    locale character varying(10) DEFAULT 'en_US'::character varying,
    timezone character varying(50) DEFAULT 'UTC'::character varying,
    manager_id character varying(36),
    last_login_at timestamp with time zone,
    login_count integer DEFAULT 0,
    mfa_enabled boolean DEFAULT false,
    settings jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    profile_id character varying(36),
    created_by character varying(255),
    updated_by character varying(255),
    phone_number character varying(20),
    email_verified boolean DEFAULT false NOT NULL,
    email_verification_token character varying(64),
    email_verification_expires_at timestamp with time zone,
    welcomed_at timestamp with time zone,
    CONSTRAINT chk_user_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'LOCKED'::character varying, 'PENDING_ACTIVATION'::character varying])::text[])))
);

ALTER TABLE ONLY platform_user FORCE ROW LEVEL SECURITY;


--
-- Name: COLUMN platform_user.email_verified; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN platform_user.email_verified IS 'TRUE once the user has confirmed ownership of their email address.';


--
-- Name: COLUMN platform_user.welcomed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN platform_user.welcomed_at IS 'Timestamp of the first successful login. Prevents the welcome email being sent twice.';


--
-- Name: profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE profile (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    is_system boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY profile FORCE ROW LEVEL SECURITY;


--
-- Name: profile_custom_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE profile_custom_rules (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    action character varying(20) NOT NULL,
    effect character varying(10) NOT NULL,
    condition_type character varying(20) NOT NULL,
    condition_json jsonb NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: profile_field_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE profile_field_permission (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    field_id character varying(36) NOT NULL,
    visibility character varying(20) DEFAULT 'VISIBLE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY profile_field_permission FORCE ROW LEVEL SECURITY;


--
-- Name: profile_object_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE profile_object_permission (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    can_create boolean DEFAULT false NOT NULL,
    can_read boolean DEFAULT false NOT NULL,
    can_edit boolean DEFAULT false NOT NULL,
    can_delete boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY profile_object_permission FORCE ROW LEVEL SECURITY;


--
-- Name: profile_system_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE profile_system_permission (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    permission_name character varying(100) NOT NULL,
    granted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY profile_system_permission FORCE ROW LEVEL SECURITY;


--
-- Name: promotion_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE promotion_item (
    id character varying(36) NOT NULL,
    promotion_id character varying(36) NOT NULL,
    item_type character varying(30) NOT NULL,
    item_id character varying(36),
    item_name character varying(200),
    action character varying(20) DEFAULT 'CREATE'::character varying NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    error_message text,
    tenant_id character varying(36) NOT NULL,
    CONSTRAINT chk_promo_item_action CHECK (((action)::text = ANY ((ARRAY['CREATE'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying, 'SKIP'::character varying])::text[]))),
    CONSTRAINT chk_promo_item_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[])))
);

ALTER TABLE ONLY promotion_item FORCE ROW LEVEL SECURITY;


--
-- Name: push_device; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE push_device (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    user_id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    platform character varying(10) NOT NULL,
    device_token character varying(500) NOT NULL,
    device_name character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: quick_action; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE quick_action (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_name character varying(200) NOT NULL,
    label character varying(200) NOT NULL,
    icon character varying(50),
    action_type character varying(30) NOT NULL,
    context character varying(10) DEFAULT 'record'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    requires_confirmation boolean DEFAULT false NOT NULL,
    confirmation_message character varying(500),
    config jsonb,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    updated_by character varying(36)
);

ALTER TABLE ONLY quick_action FORCE ROW LEVEL SECURITY;


--
-- Name: record_script; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE record_script (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    trigger_type character varying(20) NOT NULL,
    script_source text NOT NULL,
    active boolean DEFAULT true NOT NULL,
    order_sequence integer DEFAULT 0 NOT NULL,
    timeout_seconds integer DEFAULT 5 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    updated_by character varying(36)
);

ALTER TABLE ONLY record_script FORCE ROW LEVEL SECURITY;


--
-- Name: record_share; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE record_share (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    record_id character varying(36) NOT NULL,
    shared_with_id character varying(36) NOT NULL,
    shared_with_type character varying(20) NOT NULL,
    access_level character varying(20) DEFAULT 'READ'::character varying NOT NULL,
    reason character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(36),
    updated_by character varying(36)
);

ALTER TABLE ONLY record_share FORCE ROW LEVEL SECURITY;


--
-- Name: record_tombstone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE record_tombstone (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_name character varying(255) NOT NULL,
    record_id character varying(255) NOT NULL,
    deleted_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY record_tombstone FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE record_tombstone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE record_tombstone IS 'Deletion log per user-collection record; surfaced by GET /api/{collection}/_changes for offline sync';


--
-- Name: record_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE record_type (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    is_active boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY record_type FORCE ROW LEVEL SECURITY;


--
-- Name: record_type_picklist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE record_type_picklist (
    id character varying(36) NOT NULL,
    record_type_id character varying(36) NOT NULL,
    field_id character varying(36) NOT NULL,
    available_values jsonb NOT NULL,
    default_value character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: report; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE report (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(1000),
    report_type character varying(20) NOT NULL,
    primary_collection_id character varying(36) NOT NULL,
    related_joins jsonb DEFAULT '[]'::jsonb,
    columns jsonb NOT NULL,
    filters jsonb DEFAULT '[]'::jsonb,
    filter_logic character varying(500),
    row_groupings jsonb DEFAULT '[]'::jsonb,
    column_groupings jsonb DEFAULT '[]'::jsonb,
    sort_order jsonb DEFAULT '[]'::jsonb,
    chart_type character varying(20),
    chart_config jsonb,
    scope character varying(20) DEFAULT 'MY_RECORDS'::character varying,
    folder_id character varying(36),
    access_level character varying(20) DEFAULT 'PRIVATE'::character varying,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    group_by character varying(200),
    sort_by character varying(200),
    sort_direction character varying(4) DEFAULT 'ASC'::character varying
);

ALTER TABLE ONLY report FORCE ROW LEVEL SECURITY;


--
-- Name: report_folder; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE report_folder (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    access_level character varying(20) DEFAULT 'PRIVATE'::character varying,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255)
);

ALTER TABLE ONLY report_folder FORCE ROW LEVEL SECURITY;


--
-- Name: saml_provider; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE saml_provider (
    id character varying(36) DEFAULT gen_random_uuid() NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    registration_id character varying(100) NOT NULL,
    idp_entity_id character varying(500) NOT NULL,
    sso_url character varying(500) NOT NULL,
    idp_certificate text NOT NULL,
    name_id_format character varying(200),
    email_attribute character varying(200),
    profile_attribute character varying(200),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    created_by character varying(255),
    updated_by character varying(255),
    slo_url character varying(500)
);

ALTER TABLE ONLY saml_provider FORCE ROW LEVEL SECURITY;


--
-- Name: scheduled_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE scheduled_job (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    job_type character varying(20) NOT NULL,
    job_reference_id character varying(36),
    cron_expression character varying(100) NOT NULL,
    timezone character varying(50) DEFAULT 'UTC'::character varying,
    active boolean DEFAULT true,
    last_run_at timestamp with time zone,
    last_status character varying(20),
    next_run_at timestamp with time zone,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    config jsonb,
    CONSTRAINT chk_job_type CHECK (((job_type)::text = ANY ((ARRAY['FLOW'::character varying, 'SCRIPT'::character varying, 'REPORT_EXPORT'::character varying, 'DATA_EXPORT'::character varying])::text[])))
);

ALTER TABLE ONLY scheduled_job FORCE ROW LEVEL SECURITY;


--
-- Name: scim_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE scim_client (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    token_hash character varying(64) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    last_used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: script; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE script (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(500),
    script_type character varying(30) NOT NULL,
    language character varying(20) DEFAULT 'javascript'::character varying,
    source_code text NOT NULL,
    active boolean DEFAULT true,
    version integer DEFAULT 1,
    created_by character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255),
    required_permission character varying(100),
    CONSTRAINT chk_script_type CHECK (((script_type)::text = ANY ((ARRAY['BEFORE_TRIGGER'::character varying, 'AFTER_TRIGGER'::character varying, 'SCHEDULED'::character varying, 'API_ENDPOINT'::character varying, 'VALIDATION'::character varying, 'EVENT_HANDLER'::character varying, 'EMAIL_HANDLER'::character varying])::text[])))
);

ALTER TABLE ONLY script FORCE ROW LEVEL SECURITY;


--
-- Name: script_execution_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE script_execution_log (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    script_id character varying(36) NOT NULL,
    status character varying(20) NOT NULL,
    trigger_type character varying(30),
    record_id character varying(36),
    duration_ms integer,
    cpu_ms integer,
    queries_executed integer DEFAULT 0,
    dml_rows integer DEFAULT 0,
    callouts integer DEFAULT 0,
    error_message text,
    log_output text,
    executed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_exec_status CHECK (((status)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying, 'TIMEOUT'::character varying, 'GOVERNOR_LIMIT'::character varying])::text[])))
);


--
-- Name: script_trigger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE script_trigger (
    id character varying(36) NOT NULL,
    script_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    trigger_event character varying(20) NOT NULL,
    execution_order integer DEFAULT 0,
    active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_trigger_event CHECK (((trigger_event)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
);


--
-- Name: search_index; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE search_index (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    collection_name character varying(100) NOT NULL,
    record_id character varying(36) NOT NULL,
    display_value character varying(500),
    search_content text,
    search_vector tsvector,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY search_index FORCE ROW LEVEL SECURITY;


--
-- Name: security_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE security_audit_log (
    id character varying(36) NOT NULL,
    tenant_id character varying(36),
    event_type character varying(50) NOT NULL,
    event_category character varying(30) NOT NULL,
    actor_user_id character varying(36),
    actor_email character varying(320),
    target_type character varying(50),
    target_id character varying(36),
    target_name character varying(255),
    details jsonb,
    ip_address character varying(45),
    user_agent text,
    correlation_id character varying(36),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY security_audit_log FORCE ROW LEVEL SECURITY;


--
-- Name: seq_orders_order_number; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE seq_orders_order_number
    START WITH 100007
    INCREMENT BY 1
    MINVALUE 100001
    NO MAXVALUE
    CACHE 1;


--
-- Name: seq_products_sku; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE seq_products_sku
    START WITH 1010
    INCREMENT BY 1
    MINVALUE 1000
    NO MAXVALUE
    CACHE 1;


--
-- Name: setup_audit_trail; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE setup_audit_trail (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36),
    action character varying(50) NOT NULL,
    section character varying(100) NOT NULL,
    entity_type character varying(50) NOT NULL,
    entity_id character varying(36),
    entity_name character varying(200),
    old_value jsonb,
    new_value jsonb,
    "timestamp" timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_audit_action CHECK (((action)::text = ANY ((ARRAY['CREATED'::character varying, 'UPDATED'::character varying, 'DELETED'::character varying, 'ACTIVATED'::character varying, 'DEACTIVATED'::character varying])::text[])))
);

ALTER TABLE ONLY setup_audit_trail FORCE ROW LEVEL SECURITY;


--
-- Name: sms_verification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE sms_verification (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    phone character varying(20) NOT NULL,
    code_hash character varying(128) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE tenant (
    id character varying(36) NOT NULL,
    slug character varying(63) NOT NULL,
    name character varying(200) NOT NULL,
    edition character varying(20) DEFAULT 'PROFESSIONAL'::character varying NOT NULL,
    status character varying(20) DEFAULT 'PROVISIONING'::character varying NOT NULL,
    settings jsonb DEFAULT '{}'::jsonb NOT NULL,
    limits jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    email_smtp_credential_id character varying(36),
    email_from_address character varying(320),
    email_from_name character varying(200),
    auto_invite_on_create boolean DEFAULT true NOT NULL,
    cell_id character varying(64) DEFAULT 'default'::character varying NOT NULL,
    ip_allowlist_enabled boolean DEFAULT false NOT NULL,
    ip_allowlist_cidrs jsonb DEFAULT '[]'::jsonb NOT NULL,
    parent_tenant_id character varying(36),
    CONSTRAINT chk_tenant_edition CHECK (((edition)::text = ANY ((ARRAY['FREE'::character varying, 'PROFESSIONAL'::character varying, 'ENTERPRISE'::character varying, 'UNLIMITED'::character varying])::text[]))),
    CONSTRAINT chk_tenant_slug CHECK (((slug)::text ~ '^[a-z][a-z0-9-]{1,61}[a-z0-9]$'::text)),
    CONSTRAINT chk_tenant_status CHECK (((status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DECOMMISSIONED'::character varying])::text[])))
);


--
-- Name: COLUMN tenant.email_smtp_credential_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN tenant.email_smtp_credential_id IS 'FK to credential table (type=smtp). When set, overrides platform default SMTP. NULL means the tenant uses the platform default mail server.';


--
-- Name: COLUMN tenant.email_from_address; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN tenant.email_from_address IS 'Override for the From address on outbound mail (platform default applies when NULL).';


--
-- Name: COLUMN tenant.email_from_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN tenant.email_from_name IS 'Override for the From display name on outbound mail.';


--
-- Name: COLUMN tenant.auto_invite_on_create; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN tenant.auto_invite_on_create IS 'When true, a user.invite email is sent automatically on SCIM/admin user creation. When false, invites must be sent manually via POST /admin/users/{id}/invite.';


--
-- Name: COLUMN tenant.cell_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN tenant.cell_id IS 'Cell (shard) this tenant belongs to. ''default'' for everyone today; becomes per-tenant once Tier-3 cell-based deployment ships. Routes tenant traffic to a specific cell-local stack (gateway / worker / DB / Redis / NATS).';


--
-- Name: COLUMN tenant.ip_allowlist_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN tenant.ip_allowlist_enabled IS 'When true, non-admin requests to /api/** must originate from a CIDR in ip_allowlist_cidrs.';


--
-- Name: COLUMN tenant.ip_allowlist_cidrs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN tenant.ip_allowlist_cidrs IS 'JSON array of allowed CIDR ranges, e.g. ["10.0.0.0/8","192.168.0.0/16"]. Empty array = no restriction.';


--
-- Name: tenant_custom_domain; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE tenant_custom_domain (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    tenant_id character varying(36) NOT NULL,
    domain character varying(255) NOT NULL,
    verified boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    verification_token character varying(64)
);


--
-- Name: tenant_module; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE tenant_module (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    module_id character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    version character varying(20) NOT NULL,
    description character varying(1000),
    source_url character varying(2000) NOT NULL,
    jar_checksum character varying(64) NOT NULL,
    jar_size_bytes bigint,
    module_class character varying(500) NOT NULL,
    manifest jsonb NOT NULL,
    status character varying(20) DEFAULT 'INSTALLED'::character varying NOT NULL,
    installed_by character varying(36) NOT NULL,
    installed_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    s3_key character varying(2000),
    jar_signature text,
    CONSTRAINT chk_module_status CHECK (((status)::text = ANY ((ARRAY['INSTALLING'::character varying, 'INSTALLED'::character varying, 'ACTIVE'::character varying, 'DISABLED'::character varying, 'FAILED'::character varying, 'UNINSTALLING'::character varying])::text[])))
);


--
-- Name: tenant_module_action; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE tenant_module_action (
    id character varying(36) NOT NULL,
    tenant_module_id character varying(36) NOT NULL,
    action_key character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    category character varying(50),
    description character varying(500),
    config_schema jsonb,
    input_schema jsonb,
    output_schema jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id character varying(36) NOT NULL,
    updated_at timestamp with time zone,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY tenant_module_action FORCE ROW LEVEL SECURITY;


--
-- Name: tenant_otlp_target; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE tenant_otlp_target (
    tenant_id character varying(36) NOT NULL,
    endpoint character varying(1000) NOT NULL,
    headers jsonb,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY tenant_otlp_target FORCE ROW LEVEL SECURITY;


--
-- Name: ui_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ui_menu (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone,
    tenant_id character varying(36) NOT NULL,
    display_order integer DEFAULT 0,
    created_by character varying(255),
    updated_by character varying(255)
);

ALTER TABLE ONLY ui_menu FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE ui_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE ui_menu IS 'Stores UI menu configurations for navigation structure';


--
-- Name: ui_menu_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ui_menu_item (
    id character varying(36) NOT NULL,
    menu_id character varying(36) NOT NULL,
    label character varying(100) NOT NULL,
    path character varying(200) NOT NULL,
    icon character varying(100),
    display_order integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL,
    CONSTRAINT chk_ui_menu_item_display_order CHECK ((display_order >= 0))
);

ALTER TABLE ONLY ui_menu_item FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE ui_menu_item; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE ui_menu_item IS 'Stores menu items within UI menus defining navigation links';


--
-- Name: ui_page; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ui_page (
    id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    path character varying(200) NOT NULL,
    title character varying(200),
    config jsonb,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone,
    tenant_id character varying(36) NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    slug character varying(200) NOT NULL,
    published boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY ui_page FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE ui_page; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE ui_page IS 'Stores UI page configurations for the admin interface';


--
-- Name: user_api_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_api_token (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    user_id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    token_prefix character varying(10) NOT NULL,
    token_hash character varying(500) NOT NULL,
    scopes jsonb DEFAULT '["api"]'::jsonb,
    expires_at timestamp with time zone NOT NULL,
    last_used_at timestamp with time zone,
    revoked boolean DEFAULT false,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone
);


--
-- Name: user_credential; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_credential (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    user_id character varying(36) NOT NULL,
    password_hash character varying(500) NOT NULL,
    password_changed_at timestamp with time zone,
    force_change_on_login boolean DEFAULT false NOT NULL,
    failed_attempts integer DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    reset_token character varying(500),
    reset_token_expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone
);


--
-- Name: user_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_group (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    group_type character varying(20) DEFAULT 'PUBLIC'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    source character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    oidc_group_name character varying(200),
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_group_type CHECK (((group_type)::text = ANY ((ARRAY['PUBLIC'::character varying, 'QUEUE'::character varying, 'SYSTEM'::character varying])::text[])))
);

ALTER TABLE ONLY user_group FORCE ROW LEVEL SECURITY;


--
-- Name: user_recovery_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_recovery_code (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    user_id character varying(36) NOT NULL,
    code_hash character varying(500) NOT NULL,
    used boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_totp_secret; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_totp_secret (
    id character varying(36) DEFAULT (gen_random_uuid())::text NOT NULL,
    user_id character varying(36) NOT NULL,
    secret character varying(500) NOT NULL,
    verified boolean DEFAULT false NOT NULL,
    last_used_at bigint,
    mfa_failed_attempts integer DEFAULT 0 NOT NULL,
    mfa_locked_until timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: validation_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE validation_rule (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    active boolean DEFAULT true NOT NULL,
    error_condition_formula text NOT NULL,
    error_message character varying(1000) NOT NULL,
    error_field character varying(100),
    evaluate_on character varying(20) DEFAULT 'CREATE_AND_UPDATE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    enforce_on_client boolean DEFAULT false NOT NULL,
    severity character varying(10) DEFAULT 'ERROR'::character varying NOT NULL,
    CONSTRAINT chk_evaluate_on CHECK (((evaluate_on)::text = ANY ((ARRAY['CREATE'::character varying, 'UPDATE'::character varying, 'CREATE_AND_UPDATE'::character varying])::text[]))),
    CONSTRAINT chk_validation_rule_severity CHECK (((severity)::text = ANY ((ARRAY['ERROR'::character varying, 'WARNING'::character varying])::text[])))
);

ALTER TABLE ONLY validation_rule FORCE ROW LEVEL SECURITY;


--
-- Data for Name: api_call_idempotency; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: api_operation; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: api_spec; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: approval_instance; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: approval_process; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: approval_step; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: approval_step_instance; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: bulk_job; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: bulk_job_result; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: collection; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO collection (id, name, description, active, current_version, created_at, updated_at, display_name, path, tenant_id, system_collection, display_field_id, created_by, updated_by, adapter_config) VALUES ('00000000-0000-0000-0000-000000000100', '__control-plane', 'System collection representing the control plane API for authorization purposes', true, 1, '2026-07-07 03:48:42.801449+00', '2026-07-07 03:48:42.801449+00', 'Control Plane', '/control', '00000000-0000-0000-0000-000000000001', true, NULL, NULL, NULL, NULL);


--
-- Data for Name: collection_version; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: connected_app; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: connected_app_audit; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: connected_app_token; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: credential; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: credential_oauth_token; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dashboard; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: dashboard_component; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: data_export; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: delegated_admin_scope; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_campaign; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_campaign_recipient; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_suppression; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: email_template; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('f745fb65-6859-4d34-9eb4-3a41c413dbfc', 'system', 'User Invite', NULL, 'You''re invited to ${tenantName}', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Welcome to ${tenantName}</h2><p>Hi ${firstName},</p><p>You''ve been invited to join <strong>${tenantName}</strong> on Kelta. Click the button below to set your password and activate your account.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Accept invite</a></p><p style="color:#666;font-size:14px">This invitation expires in ${expiresIn}. If the button doesn''t work, copy this link into your browser:<br><span style="word-break:break-all">${actionUrl}</span></p></body></html>', 'Welcome to ${tenantName}\n\nHi ${firstName},\n\nYou''ve been invited to join ${tenantName} on Kelta. Visit the link below to set your password and activate your account.\n\n${actionUrl}\n\nThis invitation expires in ${expiresIn}.', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "required": ["tenantName", "firstName", "actionUrl"], "properties": {"email": {"type": "string"}, "actionUrl": {"type": "string"}, "expiresIn": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.invite');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('182ad7c0-2097-4cea-8c30-9b2813492799', 'system', 'Password Reset Request', NULL, 'Reset your ${tenantName} password', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Reset your password</h2><p>Hi ${firstName},</p><p>We received a request to reset the password for your ${tenantName} account. Click the button below to choose a new one.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Reset password</a></p><p style="color:#666;font-size:14px">This link expires in ${expiresIn}. If you didn''t request a reset, ignore this email.</p></body></html>', 'Reset your password\n\nHi ${firstName},\n\nWe received a request to reset the password for your ${tenantName} account. Visit the link below to choose a new one.\n\n${actionUrl}\n\nThis link expires in ${expiresIn}. If you didn''t request a reset, ignore this email.', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "required": ["actionUrl"], "properties": {"actionUrl": {"type": "string"}, "expiresIn": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.password_reset');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('236cb1ca-123f-4d34-8cd1-6cf33e0dba26', 'system', 'Password Changed', NULL, 'Your ${tenantName} password was changed', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Password changed</h2><p>Hi ${firstName},</p><p>The password for your ${tenantName} account was just changed.</p><p>If this was you, no further action is needed. If you didn''t change your password, contact your administrator immediately.</p><p style="color:#666;font-size:14px">When: ${changedAt}<br>From IP: ${ipAddress}</p></body></html>', 'Password changed\n\nHi ${firstName},\n\nThe password for your ${tenantName} account was just changed.\n\nIf this was you, no further action is needed. If you didn''t change your password, contact your administrator immediately.\n\nWhen: ${changedAt}\nFrom IP: ${ipAddress}', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "properties": {"changedAt": {"type": "string"}, "firstName": {"type": "string"}, "ipAddress": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.password_changed');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('4977fd47-916c-48cc-8633-4d8b5c82ac82', 'system', 'Verify your email', NULL, 'Verify your email for ${tenantName}', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Verify your email</h2><p>Hi ${firstName},</p><p>Please confirm <strong>${email}</strong> is your email address for ${tenantName} by clicking the button below.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Verify email</a></p><p style="color:#666;font-size:14px">This link expires in ${expiresIn}.</p></body></html>', 'Verify your email\n\nHi ${firstName},\n\nPlease confirm ${email} is your email address for ${tenantName} by visiting the link below.\n\n${actionUrl}\n\nThis link expires in ${expiresIn}.', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "required": ["email", "actionUrl"], "properties": {"email": {"type": "string"}, "actionUrl": {"type": "string"}, "expiresIn": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.email_verification');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('fb24f78e-9229-4213-9d93-5702718d41c2', 'system', 'MFA Enabled', NULL, 'Two-factor authentication enabled on your ${tenantName} account', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Two-factor authentication enabled</h2><p>Hi ${firstName},</p><p>Two-factor authentication was just enabled on your ${tenantName} account.</p><p>If this wasn''t you, contact your administrator immediately.</p><p style="color:#666;font-size:14px">When: ${changedAt}</p></body></html>', 'Two-factor authentication enabled\n\nHi ${firstName},\n\nTwo-factor authentication was just enabled on your ${tenantName} account.\n\nIf this wasn''t you, contact your administrator immediately.\n\nWhen: ${changedAt}', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "properties": {"changedAt": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.mfa_enrolled');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('a709d959-38d3-40f6-b1fd-70bc9e75740c', 'system', 'MFA Disabled', NULL, 'Two-factor authentication disabled on your ${tenantName} account', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Two-factor authentication disabled</h2><p>Hi ${firstName},</p><p>Two-factor authentication was just removed from your ${tenantName} account. Your account is now protected by password only.</p><p>If this wasn''t you, contact your administrator immediately and re-enable MFA.</p><p style="color:#666;font-size:14px">When: ${changedAt}</p></body></html>', 'Two-factor authentication disabled\n\nHi ${firstName},\n\nTwo-factor authentication was just removed from your ${tenantName} account. Your account is now protected by password only.\n\nIf this wasn''t you, contact your administrator immediately and re-enable MFA.\n\nWhen: ${changedAt}', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "properties": {"changedAt": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.mfa_disabled');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('e66a5b3c-0186-404e-8087-51a067226c64', 'system', 'Account Locked', NULL, 'Your ${tenantName} account has been locked', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Account locked</h2><p>Hi ${firstName},</p><p>Your ${tenantName} account has been locked after too many failed sign-in attempts.</p><p>The lock will be released at ${unlockAt}, or you can reset your password to unlock it sooner.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Reset password</a></p></body></html>', 'Account locked\n\nHi ${firstName},\n\nYour ${tenantName} account has been locked after too many failed sign-in attempts.\n\nThe lock will be released at ${unlockAt}, or you can reset your password to unlock it sooner:\n\n${actionUrl}', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "properties": {"unlockAt": {"type": "string"}, "actionUrl": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.account_locked');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('02d47aaf-73aa-44e7-b2fc-03b22a38049b', 'system', 'Welcome', NULL, 'Welcome to ${tenantName}', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Welcome to ${tenantName}</h2><p>Hi ${firstName},</p><p>Your account is now active. We''re glad to have you on board.</p><p style="margin:32px 0"><a href="${actionUrl}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Sign in</a></p></body></html>', 'Welcome to ${tenantName}\n\nHi ${firstName},\n\nYour account is now active. We''re glad to have you on board.\n\nSign in: ${actionUrl}', NULL, NULL, true, 'system', '2026-07-07 03:48:47.292431+00', '2026-07-07 03:48:47.292431+00', NULL, '{"type": "object", "properties": {"actionUrl": {"type": "string"}, "firstName": {"type": "string"}, "tenantName": {"type": "string"}}}', NULL, 'user.welcome');
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('6623a296-b715-4882-bf5b-db315c18045d', 'system', 'password_reset', NULL, 'Reset your password', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Reset your password</h2><p>We received a request to reset your password. Click the link below to choose a new one.</p><p style="margin:32px 0"><a href="${resetLink}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Reset password</a></p><p style="color:#666;font-size:14px">If you didn''t request a reset, you can safely ignore this email.</p></body></html>', 'Reset your password\n\nWe received a request to reset your password. Visit the link below to choose a new one.\n\n${resetLink}\n\nIf you didn''t request a reset, you can safely ignore this email.', NULL, NULL, true, 'system', '2026-07-07 03:48:47.63013+00', '2026-07-07 03:48:47.63013+00', NULL, NULL, NULL, NULL);
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('96d98052-5e0f-45d5-aea5-6149dbae1810', 'system', 'user_invite', NULL, 'You''ve been invited', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>You''ve been invited to ${tenantName}</h2><p>You''ve been invited to join <strong>${tenantName}</strong>. Click the link below to accept the invitation and set up your account.</p><p style="margin:32px 0"><a href="${inviteLink}" style="background:#0066ff;color:#fff;padding:12px 24px;border-radius:6px;text-decoration:none;display:inline-block">Accept invitation</a></p></body></html>', 'You''ve been invited to ${tenantName}\n\nYou''ve been invited to join ${tenantName}. Visit the link below to accept the invitation and set up your account.\n\n${inviteLink}', NULL, NULL, true, 'system', '2026-07-07 03:48:47.63013+00', '2026-07-07 03:48:47.63013+00', NULL, NULL, NULL, NULL);
INSERT INTO email_template (id, tenant_id, name, description, subject, body_html, body_text, related_collection_id, folder, is_active, created_by, created_at, updated_at, updated_by, variables_schema, smtp_credential_id, template_key) VALUES ('0100c36f-2eb8-476e-8669-7af04fa29272', 'system', 'welcome', NULL, 'Welcome to ${tenantName}', '<!doctype html><html><body style="font-family:system-ui,sans-serif;max-width:600px;margin:0 auto;padding:24px;color:#1a1a1a"><h2>Welcome, ${userName}!</h2><p>Your account is now active. We''re glad to have you on board.</p></body></html>', 'Welcome, ${userName}!\n\nYour account is now active. We''re glad to have you on board.', NULL, NULL, true, 'system', '2026-07-07 03:48:47.63013+00', '2026-07-07 03:48:47.63013+00', NULL, NULL, NULL, NULL);


--
-- Data for Name: environment; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: environment_promotion; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: field; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: field_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: field_version; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: file_attachment; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: flow; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: flow_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: flow_execution; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: flow_pending_resume; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: flow_step_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: flow_version; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: global_picklist; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: group_membership; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: job_execution_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: layout_assignment; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: layout_field; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: layout_related_list; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: layout_rule; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: layout_section; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: list_view; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: login_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: metadata_snapshot; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: migration_run; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: migration_step; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: note; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: oauth2_authorization; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: oauth2_authorization_consent; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: oauth2_registered_client; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: observability_settings; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO observability_settings (id, tenant_id, setting_key, setting_value, created_at, updated_at) VALUES ('fd785d73-9dd8-4537-84a1-c844c5276011', '00000000-0000-0000-0000-000000000001', 'trace_retention_days', '30', '2026-07-07 03:48:45.279888+00', '2026-07-07 03:48:45.279888+00');
INSERT INTO observability_settings (id, tenant_id, setting_key, setting_value, created_at, updated_at) VALUES ('4608dacb-24be-4de3-b38f-78a1ce18bec8', '00000000-0000-0000-0000-000000000001', 'log_retention_days', '30', '2026-07-07 03:48:45.280865+00', '2026-07-07 03:48:45.280865+00');
INSERT INTO observability_settings (id, tenant_id, setting_key, setting_value, created_at, updated_at) VALUES ('887eeb3c-40d3-4fe1-add5-6824c41010d2', '00000000-0000-0000-0000-000000000001', 'audit_retention_days', '90', '2026-07-07 03:48:45.281384+00', '2026-07-07 03:48:45.281384+00');


--
-- Data for Name: oidc_provider; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO oidc_provider (id, name, issuer, jwks_uri, active, client_id, audience, created_at, updated_at, roles_claim, roles_mapping, email_claim, username_claim, name_claim, tenant_id, created_by, updated_by, groups_claim, groups_profile_mapping, client_secret_enc, authorization_uri, token_uri, userinfo_uri, end_session_uri, discovery_status, is_internal) VALUES ('local-keycloak', 'Keycloak Local', 'http://localhost:8180/realms/emf', 'http://localhost:8180/realms/emf/protocol/openid-connect/certs', true, 'emf-ui', NULL, '2026-07-07 03:48:40.721026+00', '2026-07-07 03:48:40.721026+00', NULL, NULL, 'email', 'preferred_username', 'name', '00000000-0000-0000-0000-000000000001', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'unknown', false);
INSERT INTO oidc_provider (id, name, issuer, jwks_uri, active, client_id, audience, created_at, updated_at, roles_claim, roles_mapping, email_claim, username_claim, name_claim, tenant_id, created_by, updated_by, groups_claim, groups_profile_mapping, client_secret_enc, authorization_uri, token_uri, userinfo_uri, end_session_uri, discovery_status, is_internal) VALUES ('kelta-api-provider', 'Authentik (API)', 'https://authentik.rzware.com/application/o/kelta-api/', 'https://authentik.rzware.com/application/o/kelta-api/jwks/', true, 'emf-ui', '', '2026-07-07 03:48:45.469563+00', '2026-07-07 03:48:45.469563+00', NULL, NULL, NULL, NULL, NULL, '00000000-0000-0000-0000-000000000001', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'unknown', false);
INSERT INTO oidc_provider (id, name, issuer, jwks_uri, active, client_id, audience, created_at, updated_at, roles_claim, roles_mapping, email_claim, username_claim, name_claim, tenant_id, created_by, updated_by, groups_claim, groups_profile_mapping, client_secret_enc, authorization_uri, token_uri, userinfo_uri, end_session_uri, discovery_status, is_internal) VALUES ('7c41fced-4c9e-4b31-ba95-fdd603e79687', 'Kelta Platform (Internal)', 'https://auth.kelta.io', 'https://auth.kelta.io/oauth2/jwks', true, 'kelta-platform', NULL, '2026-07-07 03:48:45.701103+00', '2026-07-07 03:48:47.49756+00', NULL, NULL, NULL, NULL, NULL, '00000000-0000-0000-0000-000000000001', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'unknown', true);


--
-- Data for Name: package; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: package_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: package_item; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: page_layout; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: password_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: password_policy; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: picklist_dependency; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: picklist_value; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: platform_instance; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO platform_instance (id, created_at) VALUES ('95571da6-af49-40d0-931e-070f55c466bc', '2026-07-07 03:48:48.483411');


--
-- Data for Name: platform_user; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO platform_user (id, tenant_id, email, username, first_name, last_name, status, locale, timezone, manager_id, last_login_at, login_count, mfa_enabled, settings, created_at, updated_at, profile_id, created_by, updated_by, phone_number, email_verified, email_verification_token, email_verification_expires_at, welcomed_at) VALUES ('63d8a151-211d-43ca-9c10-7c3ffe79f9de', '00000000-0000-0000-0000-000000000001', 'admin@kelta.local', 'admin', 'System', 'Administrator', 'ACTIVE', 'en_US', 'UTC', NULL, NULL, 0, false, '{}', '2026-07-07 03:48:45.738602+00', '2026-07-07 03:48:45.738602+00', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', NULL, NULL, NULL, false, NULL, NULL, NULL);
INSERT INTO platform_user (id, tenant_id, email, username, first_name, last_name, status, locale, timezone, manager_id, last_login_at, login_count, mfa_enabled, settings, created_at, updated_at, profile_id, created_by, updated_by, phone_number, email_verified, email_verification_token, email_verification_expires_at, welcomed_at) VALUES ('system', 'system', 'system@kelta.io', 'system', 'System', 'User', 'ACTIVE', 'en_US', 'UTC', NULL, NULL, 0, false, '{}', '2026-07-07 03:48:47.291241+00', '2026-07-07 03:48:47.291241+00', NULL, NULL, NULL, NULL, false, NULL, NULL, NULL);


--
-- Data for Name: profile; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at, created_by, updated_by) VALUES ('c3a47939-7890-4a6c-99e2-eb1fe7a5051a', '00000000-0000-0000-0000-000000000001', 'System Administrator', 'Full, unrestricted access to all features and data', true, '2026-07-07 03:48:43.342917', '2026-07-07 03:48:43.342917', NULL, NULL);
INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at, created_by, updated_by) VALUES ('e931d2c5-8b46-4f35-ba07-b3b8477a5620', '00000000-0000-0000-0000-000000000001', 'Standard User', 'Read, create, and edit records in all collections', true, '2026-07-07 03:48:43.342917', '2026-07-07 03:48:43.342917', NULL, NULL);
INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at, created_by, updated_by) VALUES ('ef141dbf-f406-4d67-88cc-d0cc1ede2adc', '00000000-0000-0000-0000-000000000001', 'Read Only', 'View all records and reports, no create/edit/delete capability', true, '2026-07-07 03:48:43.342917', '2026-07-07 03:48:43.342917', NULL, NULL);
INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at, created_by, updated_by) VALUES ('ad144878-be73-4676-84e4-c9e780a40b6c', '00000000-0000-0000-0000-000000000001', 'Marketing User', 'Standard User plus manage email templates', true, '2026-07-07 03:48:43.342917', '2026-07-07 03:48:43.342917', NULL, NULL);
INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at, created_by, updated_by) VALUES ('64a00b09-7c56-46c9-829d-bd58d7ca224f', '00000000-0000-0000-0000-000000000001', 'Contract Manager', 'Standard User plus manage approval processes', true, '2026-07-07 03:48:43.342917', '2026-07-07 03:48:43.342917', NULL, NULL);
INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at, created_by, updated_by) VALUES ('4abb286e-eb96-442a-a7e4-205b541a7620', '00000000-0000-0000-0000-000000000001', 'Solution Manager', 'Customize application structure: collections, fields, layouts, picklists, reports', true, '2026-07-07 03:48:43.342917', '2026-07-07 03:48:43.342917', NULL, NULL);
INSERT INTO profile (id, tenant_id, name, description, is_system, created_at, updated_at, created_by, updated_by) VALUES ('13a93dbb-a144-44c0-9657-348cfd556a5d', '00000000-0000-0000-0000-000000000001', 'Minimum Access', 'Login only, no data access until explicitly granted via Permission Sets', true, '2026-07-07 03:48:43.342917', '2026-07-07 03:48:43.342917', NULL, NULL);


--
-- Data for Name: profile_custom_rules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: profile_field_permission; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: profile_object_permission; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO profile_object_permission (id, profile_id, collection_id, can_create, can_read, can_edit, can_delete, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('1f5cddb3-f762-4d67-9c1f-ea48ac451a30', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', '00000000-0000-0000-0000-000000000100', true, true, true, true, '2026-07-07 03:48:43.946594+00', '2026-07-07 03:48:43.946594+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_object_permission (id, profile_id, collection_id, can_create, can_read, can_edit, can_delete, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('f0dc0ac9-52cb-4c4f-89bd-52a987dfcbb2', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', '00000000-0000-0000-0000-000000000100', true, true, true, true, '2026-07-07 03:48:43.946594+00', '2026-07-07 03:48:43.946594+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_object_permission (id, profile_id, collection_id, can_create, can_read, can_edit, can_delete, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('149477a1-c6c8-403f-b779-15946cf615ef', 'ad144878-be73-4676-84e4-c9e780a40b6c', '00000000-0000-0000-0000-000000000100', true, true, true, true, '2026-07-07 03:48:43.946594+00', '2026-07-07 03:48:43.946594+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_object_permission (id, profile_id, collection_id, can_create, can_read, can_edit, can_delete, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('60c949be-5bfd-4540-b5d4-bb57a527290f', '64a00b09-7c56-46c9-829d-bd58d7ca224f', '00000000-0000-0000-0000-000000000100', true, true, true, true, '2026-07-07 03:48:43.946594+00', '2026-07-07 03:48:43.946594+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_object_permission (id, profile_id, collection_id, can_create, can_read, can_edit, can_delete, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('a37a3037-ccb6-4b4f-8881-1e593d1ef387', '4abb286e-eb96-442a-a7e4-205b541a7620', '00000000-0000-0000-0000-000000000100', true, true, true, true, '2026-07-07 03:48:43.946594+00', '2026-07-07 03:48:43.946594+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_object_permission (id, profile_id, collection_id, can_create, can_read, can_edit, can_delete, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('d4e6ed62-7a05-4033-8e73-beff201a2a37', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', '00000000-0000-0000-0000-000000000100', false, true, false, false, '2026-07-07 03:48:43.946594+00', '2026-07-07 03:48:43.946594+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');


--
-- Data for Name: profile_system_permission; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('0aa9cee5-6458-42c2-92c0-4f23cc7b2eec', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_TENANTS', true, '2026-07-07 03:48:45.43127+00', '2026-07-07 03:48:45.43127+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('43e88978-049a-4a2b-9371-c22a89cb1a88', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_TENANTS', false, '2026-07-07 03:48:45.43127+00', '2026-07-07 03:48:45.43127+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('84c9ffa0-f7ce-4376-86d8-44eb46a58443', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_TENANTS', false, '2026-07-07 03:48:45.43127+00', '2026-07-07 03:48:45.43127+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('882e363b-c130-4fbf-9f53-326e94fa68fd', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_TENANTS', false, '2026-07-07 03:48:45.43127+00', '2026-07-07 03:48:45.43127+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('6c1deade-42cd-457c-88f1-beeca3fc568d', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_TENANTS', false, '2026-07-07 03:48:45.43127+00', '2026-07-07 03:48:45.43127+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('746cda05-42be-4c61-929e-e8cd9223c123', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_TENANTS', false, '2026-07-07 03:48:45.43127+00', '2026-07-07 03:48:45.43127+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('b1c6f1a1-244b-4e41-8abc-a9855dc619e2', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_TENANTS', false, '2026-07-07 03:48:45.43127+00', '2026-07-07 03:48:45.43127+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('863f5980-b6f4-4043-a044-c352eb1e76dc', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_CAMPAIGNS', true, '2026-07-07 03:48:48.178395+00', '2026-07-07 03:48:48.178395+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('82c1ca89-177f-4a60-b806-f09946e37d78', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_CAMPAIGNS', false, '2026-07-07 03:48:48.178395+00', '2026-07-07 03:48:48.178395+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('9848a602-6cd3-45dd-9aba-5ca9b7335519', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_CAMPAIGNS', false, '2026-07-07 03:48:48.178395+00', '2026-07-07 03:48:48.178395+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7672ab59-77d5-48b8-8a64-21168f7b2507', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_CAMPAIGNS', true, '2026-07-07 03:48:48.178395+00', '2026-07-07 03:48:48.178395+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('a90ab8b6-9bb9-4701-a7d1-201dd28fe448', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_CAMPAIGNS', false, '2026-07-07 03:48:48.178395+00', '2026-07-07 03:48:48.178395+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('23fd6f75-f2be-4fc2-99c8-05cee2958507', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_CAMPAIGNS', false, '2026-07-07 03:48:48.178395+00', '2026-07-07 03:48:48.178395+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('ff1c5ee7-d509-4313-a6df-3e7e84165a18', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_CAMPAIGNS', false, '2026-07-07 03:48:48.178395+00', '2026-07-07 03:48:48.178395+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('b5a29626-c311-48d2-a1da-819677f6de96', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_CREDENTIALS', true, '2026-07-07 03:48:46.936304+00', '2026-07-07 03:48:46.936304+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('bb58090d-1c8b-4f46-90e4-d059b099b7a0', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'VIEW_CREDENTIALS', true, '2026-07-07 03:48:46.936304+00', '2026-07-07 03:48:46.936304+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7d0defc3-c753-4065-b4d9-01d808224384', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'VIEW_CREDENTIALS', true, '2026-07-07 03:48:46.936304+00', '2026-07-07 03:48:46.936304+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('f27ca40e-da9a-4b48-b892-004bf96b1ca5', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_DELEGATED_ADMINS', true, '2026-07-07 03:48:48.412099+00', '2026-07-07 03:48:48.412099+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7b937874-9bc4-486c-a8c7-6a4c5407c057', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_DELEGATED_ADMINS', false, '2026-07-07 03:48:48.412099+00', '2026-07-07 03:48:48.412099+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('4445b8d9-884a-4bb2-8671-7b21e29d87a4', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_DELEGATED_ADMINS', false, '2026-07-07 03:48:48.412099+00', '2026-07-07 03:48:48.412099+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('554d83ad-8cca-48c1-a19c-5a8d35bc6287', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_DELEGATED_ADMINS', false, '2026-07-07 03:48:48.412099+00', '2026-07-07 03:48:48.412099+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('098646ba-6b06-4f97-a99e-447151f544e7', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_DELEGATED_ADMINS', false, '2026-07-07 03:48:48.412099+00', '2026-07-07 03:48:48.412099+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e8de28fe-6766-491a-88fd-983784ad2afc', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_DELEGATED_ADMINS', false, '2026-07-07 03:48:48.412099+00', '2026-07-07 03:48:48.412099+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('45f27775-07cb-4bb1-8ba7-24e8240847bf', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_DELEGATED_ADMINS', false, '2026-07-07 03:48:48.412099+00', '2026-07-07 03:48:48.412099+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('c39d7026-535d-4cfb-be13-8569314d35f4', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'VIEW_SETUP', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('a60034c5-8b64-49bb-bc32-bf9ceac6be7d', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'CUSTOMIZE_APPLICATION', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e6d82450-c83b-4e1d-a0f5-55a70566729a', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_USERS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('37d609a8-d85b-4ccd-a23d-ad0039e4d4fb', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_GROUPS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('41876751-1791-4584-b693-e2fe28f8935a', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_SHARING', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('cf3bff31-3790-4a45-99d7-ae68a865de34', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_WORKFLOWS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('5e62eb8a-3172-4541-ad34-10117b6ef3f3', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_REPORTS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7cb0ff1e-dbc8-4ed0-a55d-f197ab823c20', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_EMAIL_TEMPLATES', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7837c60c-c0d3-4cb7-908b-aa333c3c5e3f', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_CONNECTED_APPS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('c354af05-0851-4ced-8156-609c3b01c6c6', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_DATA', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('23ac22cf-4de4-4a07-96bd-322f308ad39f', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'API_ACCESS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('0e028e1d-a2b4-4fa7-a2b4-6f74c27a0c7e', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'VIEW_ALL_DATA', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('69d2820c-63b7-4577-b904-e504bf5881cd', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MODIFY_ALL_DATA', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('14601fc9-0185-46cd-ad68-c09b763a8c2f', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_APPROVALS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('a8433101-3b44-499e-a715-794dfaf49806', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_LISTVIEWS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('bfa18311-d478-4ec1-b79b-29cf56f5ede5', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'VIEW_SETUP', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('ec93aeb2-1d86-4447-ad1e-b14dc8962aa5', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'CUSTOMIZE_APPLICATION', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('126b77a4-7ccc-49e9-87fe-d66582befb40', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_USERS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('bfe7d5cc-9088-4345-80c6-db82dab5a005', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_GROUPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('20012466-04d5-4963-9c40-a14367c29457', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_SHARING', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('326f44bf-4e9d-40d5-9685-6446286147fa', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_WORKFLOWS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('bc0aa408-7e4f-44c0-9628-68ff3e931f4d', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_REPORTS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7adaca48-5f27-46ac-9ac8-1a7d855328c3', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_EMAIL_TEMPLATES', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('9812b476-20ec-49d2-9a2b-75aab8bb80c2', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_CONNECTED_APPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7633d3c6-c33f-4422-b026-f2f0163385eb', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('cf2819c7-2c70-428d-8ec6-f3354ffd3fea', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'API_ACCESS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('aecd2233-e8f4-4d54-8b84-b08beebf1c21', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'VIEW_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('3848ff3e-b59b-4c22-aa4d-709cac628621', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MODIFY_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('9373d078-a635-4bd6-928c-a33e80041fd3', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_APPROVALS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('95710f7e-e555-4d29-ba96-0004f4681275', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_LISTVIEWS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('c9b45617-6a6e-4214-ac09-6d50bcdd549e', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'VIEW_SETUP', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('bf8c76df-22f0-41a1-936c-a93c10412508', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'CUSTOMIZE_APPLICATION', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('03d56564-b5f0-4ddb-be2b-a5c266ccafa8', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_USERS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('5ec850e7-d37d-40c3-b465-31a9b7a045fe', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_GROUPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('ac2bee85-64e4-4b1c-963b-717eaf360c9c', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_SHARING', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('159c935d-945b-4c35-a3ed-b777f0c14b9c', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_WORKFLOWS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('755e3e61-d70f-4222-a9d4-df7927cb698b', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_REPORTS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('4b1a07ca-8ad5-4be0-8af9-c8253ba1b5c0', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_EMAIL_TEMPLATES', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('02ec51d5-b628-4fc9-98ce-a07d77b2601a', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_CONNECTED_APPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('61cfbaa7-f389-41c8-b02f-212f47237759', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('6f445dfd-554e-485d-927f-6e022973de7f', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'API_ACCESS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e6868570-8ab3-4a76-a44b-b3e1784890f4', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'VIEW_ALL_DATA', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('7c1f6c51-988e-4f77-9440-c3632998ccb9', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MODIFY_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('276e012c-54ec-4793-80ca-ea6efd705f69', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_APPROVALS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e97fe94a-3e0b-4089-97c8-e3271cfea09d', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_LISTVIEWS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e99a2a7e-0f38-4a03-8028-9bd58a275264', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'VIEW_SETUP', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('faef1054-a7a8-4428-b08a-24e16a9d920c', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'CUSTOMIZE_APPLICATION', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('3a0b57ee-4ddc-464b-9b00-a43db50dda14', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_USERS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e9a0a2ae-a6bb-4452-a36f-a8ac31c30666', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_GROUPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('0adff4ab-bfb5-418a-bbf9-af0a025c6b43', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_SHARING', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00cc3ce7-33dd-4326-b11a-48af8448409b', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_WORKFLOWS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('3b79708b-0d5a-41f1-a77d-4536d90878e9', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_REPORTS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('cb60f522-0168-4a41-af20-32a2361881cd', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_EMAIL_TEMPLATES', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('96a0c6f5-aa54-4e9d-a0f0-c389ff7fac23', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_CONNECTED_APPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('419faa4e-33de-4a69-ae67-42a5b18335f1', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('a0cc335e-9e9b-45e6-8894-7a7c96582cc9', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'API_ACCESS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('533698d6-50d0-4db4-8ea0-4843528b28b6', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'VIEW_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('5c9da3aa-7040-4b33-b6fe-14912dc78980', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MODIFY_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('f5787d34-f9d4-4436-a5d5-a5ebdefb62ee', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_APPROVALS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e8ede527-1e7d-45b7-a829-8d8044e1449b', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_LISTVIEWS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('95966177-4350-419e-98aa-8359fea853a8', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'VIEW_SETUP', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('d2c1b21f-0cd4-414e-b071-4a2b65659928', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'CUSTOMIZE_APPLICATION', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('82fa61ce-3598-462c-a0dc-c14912a31e45', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_USERS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('fa2c87a2-e137-4459-a8d6-8a1b503d246e', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_GROUPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('dae0f7ca-ba61-4832-9afe-0f8b07cbbe41', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_SHARING', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('9152b36c-7a68-47d8-b180-d85175f6211e', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_WORKFLOWS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('1f3de089-8bc7-41e8-8493-b7f121e54f8d', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_REPORTS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('75184fcd-4ce5-46bd-acf0-42085d55fd7a', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_EMAIL_TEMPLATES', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('defa84ac-0e41-4161-90d7-07a83d910373', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_CONNECTED_APPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('1db04a94-28f6-4d21-bbfd-bdd2b35bdb83', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('4e1a0da6-bd5c-427c-a45c-d15177b3082f', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'API_ACCESS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('5bf43f38-0884-458a-b068-9d055685f625', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'VIEW_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('268a24f6-85d5-414f-9bd0-849f1bcc0ba7', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MODIFY_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('ccf236f8-51de-4dfe-a0bb-c55bf76585af', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_APPROVALS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('2173b855-c502-4ccd-be20-851df3945854', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_LISTVIEWS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('6becfdfc-50a4-4fff-99f7-9344f4e43997', '4abb286e-eb96-442a-a7e4-205b541a7620', 'VIEW_SETUP', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('60d78290-a360-4675-8645-ff6c6ec323b5', '4abb286e-eb96-442a-a7e4-205b541a7620', 'CUSTOMIZE_APPLICATION', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e9ca4045-b566-4839-bfea-784979740a6a', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_USERS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('347ce98d-66eb-4b97-a60b-12030ec5dfea', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_GROUPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('1c3f70ba-3a01-44ff-b2c9-a0a36ea07e2c', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_SHARING', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('2f587e2e-37d9-4f0a-a8d5-aabcb2b3620e', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_WORKFLOWS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('c8a96410-b47f-4bd9-aee5-b58fab557b1f', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_REPORTS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('583b7d9e-1099-461e-9e9a-f4e0c33f09ee', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_EMAIL_TEMPLATES', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('bc8ffbc7-73ea-4d95-b5fa-d89635be180b', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_CONNECTED_APPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('02774e46-f70d-472b-aea1-8b4637d24066', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('0ab4b52d-ea7d-4a13-a0bc-26b5c975f786', '4abb286e-eb96-442a-a7e4-205b541a7620', 'API_ACCESS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('3aa81be9-73d4-47a0-857f-b60f213c6246', '4abb286e-eb96-442a-a7e4-205b541a7620', 'VIEW_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e9f3d056-fb88-494c-8c2d-2fba9a30fb61', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MODIFY_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('d05c2f3d-be75-451f-bcdc-7d0dbe47acdd', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_APPROVALS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e37de6a3-60d8-46a0-b09c-3b5aad24e17b', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_LISTVIEWS', true, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('76a333eb-206e-4d2e-bedb-7917ef2a14d0', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'VIEW_SETUP', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('189c72ad-8786-4667-97c8-8c3840f815bf', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'CUSTOMIZE_APPLICATION', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('ec5ffb4a-19ee-4572-9fc6-86a8ce0f2946', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_USERS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('9b8a8ba2-e8bc-40f4-8c8e-ed8889ba493b', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_GROUPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('8ddf6bab-f693-4bb7-8b36-5c1daa1e62fb', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_SHARING', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('c27adc16-7c3b-4c53-bd28-06c172134c17', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_WORKFLOWS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('8ce9fb4e-8ea2-4e84-8449-f88045074814', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_REPORTS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('3aefa128-6b97-4d8a-aa8b-7efefe60ceef', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_EMAIL_TEMPLATES', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('4d436710-b69c-42de-86be-4fbbf603599c', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_CONNECTED_APPS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('1d60d178-f205-466a-a3a2-7de8584f10aa', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('b65e30d5-7817-4a66-b96a-a13cd3ea930d', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'API_ACCESS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('1297ec72-7255-4445-b7b5-1763e58ed0af', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'VIEW_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('11eb204e-ac74-434a-9d3c-895c1a65f9fa', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MODIFY_ALL_DATA', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('ca82c419-3204-4492-bef2-4a05b62e82ff', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_APPROVALS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('ac5995ce-e652-4568-b68c-482b2bcacc00', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_LISTVIEWS', false, '2026-07-07 03:48:43.944611+00', '2026-07-07 03:48:43.944611+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('9a8f754c-98b9-4283-96c5-f3e64ba4636c', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_API_SPECS', true, '2026-07-07 03:48:47.011223+00', '2026-07-07 03:48:47.011223+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('18c7cdac-9bd1-4692-825e-ad0eb930500f', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'VIEW_API_SPECS', true, '2026-07-07 03:48:47.011223+00', '2026-07-07 03:48:47.011223+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('80872e0b-de6e-4133-bea6-0c69dca76cb2', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'VIEW_API_SPECS', true, '2026-07-07 03:48:47.011223+00', '2026-07-07 03:48:47.011223+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('e72f451b-b6ca-48e3-b23d-7bb486c9082a', 'c3a47939-7890-4a6c-99e2-eb1fe7a5051a', 'MANAGE_SANDBOXES', true, '2026-07-07 03:48:48.536357+00', '2026-07-07 03:48:48.536357+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('337245a0-c7eb-4ed4-beed-e9aadb162109', 'e931d2c5-8b46-4f35-ba07-b3b8477a5620', 'MANAGE_SANDBOXES', false, '2026-07-07 03:48:48.536357+00', '2026-07-07 03:48:48.536357+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('70b5cd5b-38b1-40e4-b45b-65d20861687a', 'ef141dbf-f406-4d67-88cc-d0cc1ede2adc', 'MANAGE_SANDBOXES', false, '2026-07-07 03:48:48.536357+00', '2026-07-07 03:48:48.536357+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('fe145e07-da80-4b2e-97bc-08504f7542d6', 'ad144878-be73-4676-84e4-c9e780a40b6c', 'MANAGE_SANDBOXES', false, '2026-07-07 03:48:48.536357+00', '2026-07-07 03:48:48.536357+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('24789d2d-5723-4f44-9708-ae5b91f06b24', '64a00b09-7c56-46c9-829d-bd58d7ca224f', 'MANAGE_SANDBOXES', false, '2026-07-07 03:48:48.536357+00', '2026-07-07 03:48:48.536357+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('90c734fd-b1b8-4487-a5f3-ab189ae2a31e', '4abb286e-eb96-442a-a7e4-205b541a7620', 'MANAGE_SANDBOXES', false, '2026-07-07 03:48:48.536357+00', '2026-07-07 03:48:48.536357+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('46e9b247-9797-4d73-b163-bc97edf516e9', '13a93dbb-a144-44c0-9657-348cfd556a5d', 'MANAGE_SANDBOXES', false, '2026-07-07 03:48:48.536357+00', '2026-07-07 03:48:48.536357+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');


--
-- Data for Name: promotion_item; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: push_device; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: quick_action; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: record_script; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: record_share; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: record_tombstone; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: record_type; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: record_type_picklist; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: report; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: report_folder; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: saml_provider; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: scheduled_job; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: scim_client; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: script; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: script_execution_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: script_trigger; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: search_index; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: security_audit_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: setup_audit_trail; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: sms_verification; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tenant; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO tenant (id, slug, name, edition, status, settings, limits, created_at, updated_at, created_by, updated_by, email_smtp_credential_id, email_from_address, email_from_name, auto_invite_on_create, cell_id, ip_allowlist_enabled, ip_allowlist_cidrs, parent_tenant_id) VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Organization', 'ENTERPRISE', 'ACTIVE', '{}', '{}', '2026-07-07 03:48:41.039412+00', '2026-07-07 03:48:41.039412+00', NULL, NULL, NULL, NULL, NULL, true, 'default', false, '[]', NULL);
INSERT INTO tenant (id, slug, name, edition, status, settings, limits, created_at, updated_at, created_by, updated_by, email_smtp_credential_id, email_from_address, email_from_name, auto_invite_on_create, cell_id, ip_allowlist_enabled, ip_allowlist_cidrs, parent_tenant_id) VALUES ('system', 'system', 'System', 'PROFESSIONAL', 'ACTIVE', '{}', '{}', '2026-07-07 03:48:47.290308+00', '2026-07-07 03:48:47.290308+00', NULL, NULL, NULL, NULL, NULL, true, 'default', false, '[]', NULL);


--
-- Data for Name: tenant_custom_domain; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tenant_module; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tenant_module_action; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tenant_otlp_target; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: ui_menu; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO ui_menu (id, name, description, created_at, updated_at, tenant_id, display_order, created_by, updated_by) VALUES ('00000000-0000-0000-0001-000000000001', 'Data Model', 'Data model management', '2026-07-07 03:48:42.846071+00', '2026-07-07 03:48:42.846071+00', '00000000-0000-0000-0000-000000000001', 1, NULL, NULL);
INSERT INTO ui_menu (id, name, description, created_at, updated_at, tenant_id, display_order, created_by, updated_by) VALUES ('00000000-0000-0000-0001-000000000002', 'Security & Access', 'Security and access control', '2026-07-07 03:48:42.847214+00', '2026-07-07 03:48:42.847214+00', '00000000-0000-0000-0000-000000000001', 2, NULL, NULL);
INSERT INTO ui_menu (id, name, description, created_at, updated_at, tenant_id, display_order, created_by, updated_by) VALUES ('00000000-0000-0000-0001-000000000003', 'UI Builder', 'UI configuration tools', '2026-07-07 03:48:42.848246+00', '2026-07-07 03:48:42.848246+00', '00000000-0000-0000-0000-000000000001', 3, NULL, NULL);
INSERT INTO ui_menu (id, name, description, created_at, updated_at, tenant_id, display_order, created_by, updated_by) VALUES ('00000000-0000-0000-0001-000000000004', 'Automation', 'Workflow and automation tools', '2026-07-07 03:48:42.849208+00', '2026-07-07 03:48:42.849208+00', '00000000-0000-0000-0000-000000000001', 4, NULL, NULL);
INSERT INTO ui_menu (id, name, description, created_at, updated_at, tenant_id, display_order, created_by, updated_by) VALUES ('00000000-0000-0000-0001-000000000005', 'Platform', 'Platform administration', '2026-07-07 03:48:42.850019+00', '2026-07-07 03:48:42.850019+00', '00000000-0000-0000-0000-000000000001', 5, NULL, NULL);


--
-- Data for Name: ui_menu_item; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0001-000000000001', '00000000-0000-0000-0001-000000000001', 'Collections', '/collections', 'collections', 1, true, '2026-07-07 03:48:42.846724+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0001-000000000002', '00000000-0000-0000-0001-000000000001', 'Picklists', '/picklists', 'picklist', 2, true, '2026-07-07 03:48:42.846724+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0001-000000000003', '00000000-0000-0000-0001-000000000001', 'Resources', '/resources', 'resources', 3, true, '2026-07-07 03:48:42.846724+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0002-000000000001', '00000000-0000-0000-0001-000000000002', 'Users', '/users', 'users', 1, true, '2026-07-07 03:48:42.847609+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0003-000000000001', '00000000-0000-0000-0001-000000000003', 'Page Layouts', '/layouts', 'pages', 1, true, '2026-07-07 03:48:42.848609+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0003-000000000002', '00000000-0000-0000-0001-000000000003', 'List Views', '/listviews', 'browser', 2, true, '2026-07-07 03:48:42.848609+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0003-000000000003', '00000000-0000-0000-0001-000000000003', 'Pages', '/pages', 'pages', 3, true, '2026-07-07 03:48:42.848609+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0003-000000000004', '00000000-0000-0000-0001-000000000003', 'Menus', '/menus', 'menus', 4, true, '2026-07-07 03:48:42.848609+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0004-000000000001', '00000000-0000-0000-0001-000000000004', 'Workflow Rules', '/workflow-rules', 'workflow', 1, true, '2026-07-07 03:48:42.849596+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0004-000000000002', '00000000-0000-0000-0001-000000000004', 'Approvals', '/approvals', 'approval', 2, true, '2026-07-07 03:48:42.849596+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0004-000000000003', '00000000-0000-0000-0001-000000000004', 'Flows', '/flows', 'flow', 3, true, '2026-07-07 03:48:42.849596+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0004-000000000004', '00000000-0000-0000-0001-000000000004', 'Scheduled Jobs', '/scheduled-jobs', 'schedule', 4, true, '2026-07-07 03:48:42.849596+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0004-000000000005', '00000000-0000-0000-0001-000000000004', 'Scripts', '/scripts', 'script', 5, true, '2026-07-07 03:48:42.849596+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000001', '00000000-0000-0000-0001-000000000005', 'System Health', '/system-health', 'health', 1, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000002', '00000000-0000-0000-0001-000000000005', 'Workers', '/workers', 'workers', 2, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000003', '00000000-0000-0000-0001-000000000005', 'Tenants', '/tenants', 'tenants', 3, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000004', '00000000-0000-0000-0001-000000000005', 'Tenant Dashboard', '/tenant-dashboard', 'dashboard', 4, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000005', '00000000-0000-0000-0001-000000000005', 'Audit Trail', '/audit-trail', 'audit', 5, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000006', '00000000-0000-0000-0001-000000000005', 'Governor Limits', '/governor-limits', 'limits', 6, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000007', '00000000-0000-0000-0001-000000000005', 'Plugins', '/plugins', 'plugin', 7, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000008', '00000000-0000-0000-0001-000000000005', 'Webhooks', '/webhooks', 'webhook', 8, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000009', '00000000-0000-0000-0001-000000000005', 'Connected Apps', '/connected-apps', 'apps', 9, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000010', '00000000-0000-0000-0001-000000000005', 'Email Templates', '/email-templates', 'email', 10, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000011', '00000000-0000-0000-0001-000000000005', 'Packages', '/packages', 'packages', 11, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000012', '00000000-0000-0000-0001-000000000005', 'Migrations', '/migrations', 'migration', 12, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0005-000000000013', '00000000-0000-0000-0001-000000000005', 'Bulk Jobs', '/bulk-jobs', 'bulk', 13, true, '2026-07-07 03:48:42.850405+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');
INSERT INTO ui_menu_item (id, menu_id, label, path, icon, display_order, active, created_at, updated_at, created_by, updated_by, tenant_id) VALUES ('00000000-0000-0001-0002-000000000008', '00000000-0000-0000-0001-000000000002', 'OIDC Providers', '/oidc-providers', 'oidc', 2, true, '2026-07-07 03:48:42.847609+00', '2026-07-07 03:48:43.956014+00', NULL, NULL, '00000000-0000-0000-0000-000000000001');


--
-- Data for Name: ui_page; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: user_api_token; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: user_credential; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO user_credential (id, user_id, password_hash, password_changed_at, force_change_on_login, failed_attempts, locked_until, reset_token, reset_token_expires_at, created_at, updated_at) VALUES ('33ac5ed7-0100-4261-9314-9b56f684630c', '63d8a151-211d-43ca-9c10-7c3ffe79f9de', '$2a$10$zAQaSHX1XSR1bwUL3pz9EOzecplsxInVizZc9HwLf7xPluSiE1EP6', NULL, false, 0, NULL, NULL, NULL, '2026-07-07 03:48:45.738602+00', NULL);


--
-- Data for Name: user_group; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO user_group (id, tenant_id, name, description, group_type, created_at, updated_at, source, oidc_group_name, created_by, updated_by) VALUES ('00000000-0000-0000-0000-00000000-000', '00000000-0000-0000-0000-000000000001', 'All Authenticated Users', 'System group containing all authenticated users', 'SYSTEM', '2026-07-07 03:48:42.91096+00', '2026-07-07 03:48:42.91096+00', 'SYSTEM', NULL, NULL, NULL);


--
-- Data for Name: user_recovery_code; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: user_totp_secret; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: validation_rule; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: seq_orders_order_number; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('seq_orders_order_number', 100007, false);


--
-- Name: seq_products_sku; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('seq_products_sku', 1010, false);


--
-- Name: api_call_idempotency api_call_idempotency_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_call_idempotency
    ADD CONSTRAINT api_call_idempotency_pkey PRIMARY KEY (tenant_id, idempotency_key);


--
-- Name: api_operation api_operation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_operation
    ADD CONSTRAINT api_operation_pkey PRIMARY KEY (id);


--
-- Name: api_spec api_spec_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_spec
    ADD CONSTRAINT api_spec_pkey PRIMARY KEY (id);


--
-- Name: approval_instance approval_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_instance
    ADD CONSTRAINT approval_instance_pkey PRIMARY KEY (id);


--
-- Name: approval_process approval_process_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_process
    ADD CONSTRAINT approval_process_pkey PRIMARY KEY (id);


--
-- Name: approval_step_instance approval_step_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_step_instance
    ADD CONSTRAINT approval_step_instance_pkey PRIMARY KEY (id);


--
-- Name: approval_step approval_step_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_step
    ADD CONSTRAINT approval_step_pkey PRIMARY KEY (id);


--
-- Name: bulk_job bulk_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY bulk_job
    ADD CONSTRAINT bulk_job_pkey PRIMARY KEY (id);


--
-- Name: bulk_job_result bulk_job_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY bulk_job_result
    ADD CONSTRAINT bulk_job_result_pkey PRIMARY KEY (id);


--
-- Name: collection collection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection
    ADD CONSTRAINT collection_pkey PRIMARY KEY (id);


--
-- Name: collection_version collection_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection_version
    ADD CONSTRAINT collection_version_pkey PRIMARY KEY (id);


--
-- Name: connected_app_audit connected_app_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app_audit
    ADD CONSTRAINT connected_app_audit_pkey PRIMARY KEY (id);


--
-- Name: connected_app connected_app_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app
    ADD CONSTRAINT connected_app_client_id_key UNIQUE (client_id);


--
-- Name: connected_app connected_app_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app
    ADD CONSTRAINT connected_app_pkey PRIMARY KEY (id);


--
-- Name: connected_app_token connected_app_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app_token
    ADD CONSTRAINT connected_app_token_pkey PRIMARY KEY (id);


--
-- Name: credential_oauth_token credential_oauth_token_credential_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY credential_oauth_token
    ADD CONSTRAINT credential_oauth_token_credential_id_key UNIQUE (credential_id);


--
-- Name: credential_oauth_token credential_oauth_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY credential_oauth_token
    ADD CONSTRAINT credential_oauth_token_pkey PRIMARY KEY (id);


--
-- Name: credential credential_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY credential
    ADD CONSTRAINT credential_pkey PRIMARY KEY (id);


--
-- Name: dashboard_component dashboard_component_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard_component
    ADD CONSTRAINT dashboard_component_pkey PRIMARY KEY (id);


--
-- Name: dashboard dashboard_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard
    ADD CONSTRAINT dashboard_pkey PRIMARY KEY (id);


--
-- Name: data_export data_export_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_export
    ADD CONSTRAINT data_export_pkey PRIMARY KEY (id);


--
-- Name: delegated_admin_scope delegated_admin_scope_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY delegated_admin_scope
    ADD CONSTRAINT delegated_admin_scope_pkey PRIMARY KEY (id);


--
-- Name: email_campaign email_campaign_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_campaign
    ADD CONSTRAINT email_campaign_pkey PRIMARY KEY (id);


--
-- Name: email_campaign_recipient email_campaign_recipient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_campaign_recipient
    ADD CONSTRAINT email_campaign_recipient_pkey PRIMARY KEY (id);


--
-- Name: email_log email_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_log
    ADD CONSTRAINT email_log_pkey PRIMARY KEY (id);


--
-- Name: email_suppression email_suppression_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_suppression
    ADD CONSTRAINT email_suppression_pkey PRIMARY KEY (id);


--
-- Name: email_template email_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_template
    ADD CONSTRAINT email_template_pkey PRIMARY KEY (id);


--
-- Name: environment environment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment
    ADD CONSTRAINT environment_pkey PRIMARY KEY (id);


--
-- Name: environment_promotion environment_promotion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment_promotion
    ADD CONSTRAINT environment_promotion_pkey PRIMARY KEY (id);


--
-- Name: field_history field_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_history
    ADD CONSTRAINT field_history_pkey PRIMARY KEY (id);


--
-- Name: field field_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field
    ADD CONSTRAINT field_pkey PRIMARY KEY (id);


--
-- Name: field_version field_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_version
    ADD CONSTRAINT field_version_pkey PRIMARY KEY (id);


--
-- Name: file_attachment file_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY file_attachment
    ADD CONSTRAINT file_attachment_pkey PRIMARY KEY (id);


--
-- Name: flow_audit_log flow_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_audit_log
    ADD CONSTRAINT flow_audit_log_pkey PRIMARY KEY (id);


--
-- Name: flow_execution flow_execution_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_execution
    ADD CONSTRAINT flow_execution_pkey PRIMARY KEY (id);


--
-- Name: flow_pending_resume flow_pending_resume_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_pending_resume
    ADD CONSTRAINT flow_pending_resume_pkey PRIMARY KEY (id);


--
-- Name: flow flow_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow
    ADD CONSTRAINT flow_pkey PRIMARY KEY (id);


--
-- Name: flow_step_log flow_step_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_step_log
    ADD CONSTRAINT flow_step_log_pkey PRIMARY KEY (id);


--
-- Name: flow_version flow_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_version
    ADD CONSTRAINT flow_version_pkey PRIMARY KEY (id);


--
-- Name: global_picklist global_picklist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_picklist
    ADD CONSTRAINT global_picklist_pkey PRIMARY KEY (id);


--
-- Name: group_membership group_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY group_membership
    ADD CONSTRAINT group_membership_pkey PRIMARY KEY (id);


--
-- Name: job_execution_log job_execution_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY job_execution_log
    ADD CONSTRAINT job_execution_log_pkey PRIMARY KEY (id);


--
-- Name: layout_assignment layout_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_assignment
    ADD CONSTRAINT layout_assignment_pkey PRIMARY KEY (id);


--
-- Name: layout_field layout_field_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_field
    ADD CONSTRAINT layout_field_pkey PRIMARY KEY (id);


--
-- Name: layout_related_list layout_related_list_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_related_list
    ADD CONSTRAINT layout_related_list_pkey PRIMARY KEY (id);


--
-- Name: layout_rule layout_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_rule
    ADD CONSTRAINT layout_rule_pkey PRIMARY KEY (id);


--
-- Name: layout_section layout_section_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_section
    ADD CONSTRAINT layout_section_pkey PRIMARY KEY (id);


--
-- Name: list_view list_view_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY list_view
    ADD CONSTRAINT list_view_pkey PRIMARY KEY (id);


--
-- Name: login_history login_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY login_history
    ADD CONSTRAINT login_history_pkey PRIMARY KEY (id);


--
-- Name: metadata_snapshot metadata_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY metadata_snapshot
    ADD CONSTRAINT metadata_snapshot_pkey PRIMARY KEY (id);


--
-- Name: migration_run migration_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY migration_run
    ADD CONSTRAINT migration_run_pkey PRIMARY KEY (id);


--
-- Name: migration_step migration_step_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY migration_step
    ADD CONSTRAINT migration_step_pkey PRIMARY KEY (id);


--
-- Name: note note_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY note
    ADD CONSTRAINT note_pkey PRIMARY KEY (id);


--
-- Name: observability_settings observability_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY observability_settings
    ADD CONSTRAINT observability_settings_pkey PRIMARY KEY (id);


--
-- Name: oidc_provider oidc_provider_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY oidc_provider
    ADD CONSTRAINT oidc_provider_pkey PRIMARY KEY (id);


--
-- Name: package_history package_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY package_history
    ADD CONSTRAINT package_history_pkey PRIMARY KEY (id);


--
-- Name: package_item package_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY package_item
    ADD CONSTRAINT package_item_pkey PRIMARY KEY (id);


--
-- Name: package package_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY package
    ADD CONSTRAINT package_pkey PRIMARY KEY (id);


--
-- Name: page_layout page_layout_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY page_layout
    ADD CONSTRAINT page_layout_pkey PRIMARY KEY (id);


--
-- Name: password_history password_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY password_history
    ADD CONSTRAINT password_history_pkey PRIMARY KEY (id);


--
-- Name: password_policy password_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY password_policy
    ADD CONSTRAINT password_policy_pkey PRIMARY KEY (id);


--
-- Name: password_policy password_policy_tenant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY password_policy
    ADD CONSTRAINT password_policy_tenant_id_key UNIQUE (tenant_id);


--
-- Name: picklist_dependency picklist_dependency_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_dependency
    ADD CONSTRAINT picklist_dependency_pkey PRIMARY KEY (id);


--
-- Name: picklist_value picklist_value_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_value
    ADD CONSTRAINT picklist_value_pkey PRIMARY KEY (id);


--
-- Name: oauth2_authorization pk_oauth2_authorization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY oauth2_authorization
    ADD CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id);


--
-- Name: oauth2_authorization_consent pk_oauth2_authorization_consent; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY oauth2_authorization_consent
    ADD CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (registered_client_id, principal_name);


--
-- Name: oauth2_registered_client pk_oauth2_registered_client; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY oauth2_registered_client
    ADD CONSTRAINT pk_oauth2_registered_client PRIMARY KEY (id);


--
-- Name: user_credential pk_user_credential; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_credential
    ADD CONSTRAINT pk_user_credential PRIMARY KEY (id);


--
-- Name: platform_instance platform_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY platform_instance
    ADD CONSTRAINT platform_instance_pkey PRIMARY KEY (id);


--
-- Name: platform_user platform_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY platform_user
    ADD CONSTRAINT platform_user_pkey PRIMARY KEY (id);


--
-- Name: profile_custom_rules profile_custom_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_custom_rules
    ADD CONSTRAINT profile_custom_rules_pkey PRIMARY KEY (id);


--
-- Name: profile_field_permission profile_field_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_field_permission
    ADD CONSTRAINT profile_field_permission_pkey PRIMARY KEY (id);


--
-- Name: profile_field_permission profile_field_permission_profile_id_field_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_field_permission
    ADD CONSTRAINT profile_field_permission_profile_id_field_id_key UNIQUE (profile_id, field_id);


--
-- Name: profile_object_permission profile_object_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_object_permission
    ADD CONSTRAINT profile_object_permission_pkey PRIMARY KEY (id);


--
-- Name: profile_object_permission profile_object_permission_profile_id_collection_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_object_permission
    ADD CONSTRAINT profile_object_permission_profile_id_collection_id_key UNIQUE (profile_id, collection_id);


--
-- Name: profile profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile
    ADD CONSTRAINT profile_pkey PRIMARY KEY (id);


--
-- Name: profile_system_permission profile_system_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_system_permission
    ADD CONSTRAINT profile_system_permission_pkey PRIMARY KEY (id);


--
-- Name: profile_system_permission profile_system_permission_profile_id_permission_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_system_permission
    ADD CONSTRAINT profile_system_permission_profile_id_permission_name_key UNIQUE (profile_id, permission_name);


--
-- Name: profile profile_tenant_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile
    ADD CONSTRAINT profile_tenant_id_name_key UNIQUE (tenant_id, name);


--
-- Name: promotion_item promotion_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY promotion_item
    ADD CONSTRAINT promotion_item_pkey PRIMARY KEY (id);


--
-- Name: push_device push_device_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY push_device
    ADD CONSTRAINT push_device_pkey PRIMARY KEY (id);


--
-- Name: quick_action quick_action_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY quick_action
    ADD CONSTRAINT quick_action_pkey PRIMARY KEY (id);


--
-- Name: record_script record_script_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_script
    ADD CONSTRAINT record_script_pkey PRIMARY KEY (id);


--
-- Name: record_share record_share_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_share
    ADD CONSTRAINT record_share_pkey PRIMARY KEY (id);


--
-- Name: record_tombstone record_tombstone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_tombstone
    ADD CONSTRAINT record_tombstone_pkey PRIMARY KEY (id);


--
-- Name: record_type_picklist record_type_picklist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type_picklist
    ADD CONSTRAINT record_type_picklist_pkey PRIMARY KEY (id);


--
-- Name: record_type record_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type
    ADD CONSTRAINT record_type_pkey PRIMARY KEY (id);


--
-- Name: report_folder report_folder_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report_folder
    ADD CONSTRAINT report_folder_pkey PRIMARY KEY (id);


--
-- Name: report report_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report
    ADD CONSTRAINT report_pkey PRIMARY KEY (id);


--
-- Name: saml_provider saml_provider_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY saml_provider
    ADD CONSTRAINT saml_provider_pkey PRIMARY KEY (id);


--
-- Name: saml_provider saml_provider_tenant_id_registration_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY saml_provider
    ADD CONSTRAINT saml_provider_tenant_id_registration_id_key UNIQUE (tenant_id, registration_id);


--
-- Name: scheduled_job scheduled_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scheduled_job
    ADD CONSTRAINT scheduled_job_pkey PRIMARY KEY (id);


--
-- Name: scim_client scim_client_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scim_client
    ADD CONSTRAINT scim_client_pkey PRIMARY KEY (id);


--
-- Name: scim_client scim_client_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scim_client
    ADD CONSTRAINT scim_client_token_hash_key UNIQUE (token_hash);


--
-- Name: script_execution_log script_execution_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script_execution_log
    ADD CONSTRAINT script_execution_log_pkey PRIMARY KEY (id);


--
-- Name: script script_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script
    ADD CONSTRAINT script_pkey PRIMARY KEY (id);


--
-- Name: script_trigger script_trigger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script_trigger
    ADD CONSTRAINT script_trigger_pkey PRIMARY KEY (id);


--
-- Name: search_index search_index_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY search_index
    ADD CONSTRAINT search_index_pkey PRIMARY KEY (id);


--
-- Name: security_audit_log security_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_audit_log
    ADD CONSTRAINT security_audit_log_pkey PRIMARY KEY (id);


--
-- Name: setup_audit_trail setup_audit_trail_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY setup_audit_trail
    ADD CONSTRAINT setup_audit_trail_pkey PRIMARY KEY (id);


--
-- Name: sms_verification sms_verification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY sms_verification
    ADD CONSTRAINT sms_verification_pkey PRIMARY KEY (id);


--
-- Name: tenant_custom_domain tenant_custom_domain_domain_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_custom_domain
    ADD CONSTRAINT tenant_custom_domain_domain_key UNIQUE (domain);


--
-- Name: tenant_custom_domain tenant_custom_domain_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_custom_domain
    ADD CONSTRAINT tenant_custom_domain_pkey PRIMARY KEY (id);


--
-- Name: tenant_module_action tenant_module_action_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_module_action
    ADD CONSTRAINT tenant_module_action_pkey PRIMARY KEY (id);


--
-- Name: tenant_module tenant_module_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_module
    ADD CONSTRAINT tenant_module_pkey PRIMARY KEY (id);


--
-- Name: tenant_otlp_target tenant_otlp_target_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_otlp_target
    ADD CONSTRAINT tenant_otlp_target_pkey PRIMARY KEY (tenant_id);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);


--
-- Name: tenant tenant_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant
    ADD CONSTRAINT tenant_slug_key UNIQUE (slug);


--
-- Name: ui_menu_item ui_menu_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu_item
    ADD CONSTRAINT ui_menu_item_pkey PRIMARY KEY (id);


--
-- Name: ui_menu ui_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu
    ADD CONSTRAINT ui_menu_pkey PRIMARY KEY (id);


--
-- Name: ui_page ui_page_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_page
    ADD CONSTRAINT ui_page_pkey PRIMARY KEY (id);


--
-- Name: collection_version uk_collection_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection_version
    ADD CONSTRAINT uk_collection_version UNIQUE (collection_id, version);


--
-- Name: field uk_field_collection_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field
    ADD CONSTRAINT uk_field_collection_name UNIQUE (collection_id, name);


--
-- Name: migration_step uk_migration_step_run_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY migration_step
    ADD CONSTRAINT uk_migration_step_run_number UNIQUE (migration_run_id, step_number);


--
-- Name: search_index uk_search_index_tenant_collection_record; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY search_index
    ADD CONSTRAINT uk_search_index_tenant_collection_record UNIQUE (tenant_id, collection_name, record_id);


--
-- Name: api_operation uq_api_operation_spec_op; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_operation
    ADD CONSTRAINT uq_api_operation_spec_op UNIQUE (spec_id, synthetic_op_id);


--
-- Name: api_spec uq_api_spec_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_spec
    ADD CONSTRAINT uq_api_spec_tenant_name UNIQUE (tenant_id, name);


--
-- Name: approval_process uq_approval_process; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_process
    ADD CONSTRAINT uq_approval_process UNIQUE (tenant_id, collection_id, name);


--
-- Name: approval_step uq_approval_step; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_step
    ADD CONSTRAINT uq_approval_step UNIQUE (approval_process_id, step_number);


--
-- Name: email_campaign_recipient uq_campaign_recipient; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_campaign_recipient
    ADD CONSTRAINT uq_campaign_recipient UNIQUE (campaign_id, email);


--
-- Name: collection uq_collection_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection
    ADD CONSTRAINT uq_collection_tenant_name UNIQUE (tenant_id, name);


--
-- Name: connected_app uq_connected_app; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app
    ADD CONSTRAINT uq_connected_app UNIQUE (tenant_id, name);


--
-- Name: credential uq_credential_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY credential
    ADD CONSTRAINT uq_credential_tenant_name UNIQUE (tenant_id, name);


--
-- Name: email_template uq_email_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_template
    ADD CONSTRAINT uq_email_template UNIQUE (tenant_id, name);


--
-- Name: flow uq_flow; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow
    ADD CONSTRAINT uq_flow UNIQUE (tenant_id, name);


--
-- Name: flow_version uq_flow_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_version
    ADD CONSTRAINT uq_flow_version UNIQUE (flow_id, version_number);


--
-- Name: global_picklist uq_global_picklist; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_picklist
    ADD CONSTRAINT uq_global_picklist UNIQUE (tenant_id, name);


--
-- Name: group_membership uq_group_membership; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY group_membership
    ADD CONSTRAINT uq_group_membership UNIQUE (group_id, member_type, member_id);


--
-- Name: user_group uq_group_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group
    ADD CONSTRAINT uq_group_name UNIQUE (tenant_id, name);


--
-- Name: page_layout uq_layout; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY page_layout
    ADD CONSTRAINT uq_layout UNIQUE (tenant_id, collection_id, name);


--
-- Name: layout_assignment uq_layout_assign; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_assignment
    ADD CONSTRAINT uq_layout_assign UNIQUE (tenant_id, collection_id, profile_id, record_type_id);


--
-- Name: layout_rule uq_layout_rule_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_rule
    ADD CONSTRAINT uq_layout_rule_name UNIQUE (tenant_id, layout_id, name);


--
-- Name: list_view uq_list_view; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY list_view
    ADD CONSTRAINT uq_list_view UNIQUE (tenant_id, collection_id, name, created_by);


--
-- Name: observability_settings uq_observability_settings_tenant_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY observability_settings
    ADD CONSTRAINT uq_observability_settings_tenant_key UNIQUE (tenant_id, setting_key);


--
-- Name: oidc_provider uq_oidc_provider_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY oidc_provider
    ADD CONSTRAINT uq_oidc_provider_tenant_name UNIQUE (tenant_id, name);


--
-- Name: package uq_package_tenant_name_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY package
    ADD CONSTRAINT uq_package_tenant_name_version UNIQUE (tenant_id, name, version);


--
-- Name: picklist_dependency uq_picklist_dep; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_dependency
    ADD CONSTRAINT uq_picklist_dep UNIQUE (controlling_field_id, dependent_field_id);


--
-- Name: picklist_value uq_picklist_value; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_value
    ADD CONSTRAINT uq_picklist_value UNIQUE (picklist_source_type, picklist_source_id, value);


--
-- Name: push_device uq_push_device_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY push_device
    ADD CONSTRAINT uq_push_device_token UNIQUE (tenant_id, device_token);


--
-- Name: record_script uq_record_script_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_script
    ADD CONSTRAINT uq_record_script_name UNIQUE (tenant_id, collection_id, name);


--
-- Name: record_share uq_record_share; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_share
    ADD CONSTRAINT uq_record_share UNIQUE (tenant_id, collection_id, record_id, shared_with_id);


--
-- Name: record_type uq_record_type_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type
    ADD CONSTRAINT uq_record_type_name UNIQUE (tenant_id, collection_id, name);


--
-- Name: record_type_picklist uq_record_type_picklist; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type_picklist
    ADD CONSTRAINT uq_record_type_picklist UNIQUE (record_type_id, field_id);


--
-- Name: report_folder uq_report_folder; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report_folder
    ADD CONSTRAINT uq_report_folder UNIQUE (tenant_id, name, created_by);


--
-- Name: scheduled_job uq_scheduled_job; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scheduled_job
    ADD CONSTRAINT uq_scheduled_job UNIQUE (tenant_id, name);


--
-- Name: script uq_script; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script
    ADD CONSTRAINT uq_script UNIQUE (tenant_id, name);


--
-- Name: script_trigger uq_script_trigger; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script_trigger
    ADD CONSTRAINT uq_script_trigger UNIQUE (script_id, collection_id, trigger_event);


--
-- Name: email_suppression uq_suppression_tenant_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_suppression
    ADD CONSTRAINT uq_suppression_tenant_email UNIQUE (tenant_id, email);


--
-- Name: tenant_module uq_tenant_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_module
    ADD CONSTRAINT uq_tenant_module UNIQUE (tenant_id, module_id);


--
-- Name: connected_app_token uq_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app_token
    ADD CONSTRAINT uq_token_hash UNIQUE (token_hash);


--
-- Name: ui_menu uq_ui_menu_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu
    ADD CONSTRAINT uq_ui_menu_tenant_name UNIQUE (tenant_id, name);


--
-- Name: ui_page uq_ui_page_tenant_path; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_page
    ADD CONSTRAINT uq_ui_page_tenant_path UNIQUE (tenant_id, path);


--
-- Name: ui_page uq_ui_page_tenant_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_page
    ADD CONSTRAINT uq_ui_page_tenant_slug UNIQUE (tenant_id, slug);


--
-- Name: user_api_token uq_user_api_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_api_token
    ADD CONSTRAINT uq_user_api_token_hash UNIQUE (token_hash);


--
-- Name: user_api_token uq_user_api_token_name_per_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_api_token
    ADD CONSTRAINT uq_user_api_token_name_per_user UNIQUE (user_id, name);


--
-- Name: user_credential uq_user_credential_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_credential
    ADD CONSTRAINT uq_user_credential_user UNIQUE (user_id);


--
-- Name: platform_user uq_user_tenant_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY platform_user
    ADD CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email);


--
-- Name: validation_rule uq_validation_rule_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY validation_rule
    ADD CONSTRAINT uq_validation_rule_name UNIQUE (tenant_id, collection_id, name);


--
-- Name: user_api_token user_api_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_api_token
    ADD CONSTRAINT user_api_token_pkey PRIMARY KEY (id);


--
-- Name: user_group user_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group
    ADD CONSTRAINT user_group_pkey PRIMARY KEY (id);


--
-- Name: user_recovery_code user_recovery_code_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_recovery_code
    ADD CONSTRAINT user_recovery_code_pkey PRIMARY KEY (id);


--
-- Name: user_totp_secret user_totp_secret_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_totp_secret
    ADD CONSTRAINT user_totp_secret_pkey PRIMARY KEY (id);


--
-- Name: user_totp_secret user_totp_secret_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_totp_secret
    ADD CONSTRAINT user_totp_secret_user_id_key UNIQUE (user_id);


--
-- Name: validation_rule validation_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY validation_rule
    ADD CONSTRAINT validation_rule_pkey PRIMARY KEY (id);


--
-- Name: idx_api_call_idempotency_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_call_idempotency_expires ON api_call_idempotency USING btree (expires_at);


--
-- Name: idx_api_operation_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_operation_search ON api_operation USING gin (search_text gin_trgm_ops);


--
-- Name: idx_api_operation_spec; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_operation_spec ON api_operation USING btree (spec_id);


--
-- Name: idx_api_operation_tags; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_operation_tags ON api_operation USING gin (tags jsonb_path_ops);


--
-- Name: idx_api_operation_tenant_method; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_operation_tenant_method ON api_operation USING btree (tenant_id, http_method);


--
-- Name: idx_api_spec_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_spec_tenant ON api_spec USING btree (tenant_id) WHERE (is_active = true);


--
-- Name: idx_api_spec_tenant_all; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_spec_tenant_all ON api_spec USING btree (tenant_id);


--
-- Name: idx_approval_instance_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_approval_instance_record ON approval_instance USING btree (collection_id, record_id);


--
-- Name: idx_approval_process_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_approval_process_tenant ON approval_process USING btree (tenant_id, collection_id);


--
-- Name: idx_approval_step_instance_assigned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_approval_step_instance_assigned ON approval_step_instance USING btree (assigned_to, status);


--
-- Name: idx_attachment_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_tenant ON file_attachment USING btree (tenant_id);


--
-- Name: idx_attachment_tenant_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachment_tenant_record ON file_attachment USING btree (tenant_id, collection_id, record_id, uploaded_at DESC);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_entity ON setup_audit_trail USING btree (entity_type, entity_id);


--
-- Name: idx_audit_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_tenant_time ON setup_audit_trail USING btree (tenant_id, "timestamp" DESC);


--
-- Name: idx_audit_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_user ON setup_audit_trail USING btree (user_id, "timestamp" DESC);


--
-- Name: idx_bulk_job_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bulk_job_status ON bulk_job USING btree (status);


--
-- Name: idx_bulk_job_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bulk_job_tenant ON bulk_job USING btree (tenant_id, created_at DESC);


--
-- Name: idx_bulk_result_job; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bulk_result_job ON bulk_job_result USING btree (bulk_job_id, record_index);


--
-- Name: idx_ca_audit_app; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ca_audit_app ON connected_app_audit USING btree (connected_app_id, created_at DESC);


--
-- Name: idx_ca_audit_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ca_audit_tenant ON connected_app_audit USING btree (tenant_id, created_at DESC);


--
-- Name: idx_campaign_recipient_campaign; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_campaign_recipient_campaign ON email_campaign_recipient USING btree (campaign_id, status);


--
-- Name: idx_collection_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_active ON collection USING btree (active);


--
-- Name: idx_collection_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_created_at ON collection USING btree (created_at);


--
-- Name: idx_collection_display_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_display_name ON collection USING btree (display_name);


--
-- Name: idx_collection_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_name ON collection USING btree (name);


--
-- Name: idx_collection_system; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_system ON collection USING btree (system_collection);


--
-- Name: idx_collection_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_tenant ON collection USING btree (tenant_id);


--
-- Name: idx_collection_version_collection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_version_collection_id ON collection_version USING btree (collection_id);


--
-- Name: idx_collection_version_schema_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_version_schema_gin ON collection_version USING gin (schema jsonb_path_ops);


--
-- Name: idx_collection_version_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collection_version_version ON collection_version USING btree (version);


--
-- Name: idx_connected_app_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_connected_app_client ON connected_app USING btree (client_id);


--
-- Name: idx_connected_app_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_connected_app_tenant ON connected_app USING btree (tenant_id);


--
-- Name: idx_credential_oauth_token_credential; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credential_oauth_token_credential ON credential_oauth_token USING btree (credential_id);


--
-- Name: idx_credential_oauth_token_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credential_oauth_token_expires_at ON credential_oauth_token USING btree (expires_at);


--
-- Name: idx_credential_oauth_token_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credential_oauth_token_tenant ON credential_oauth_token USING btree (tenant_id);


--
-- Name: idx_credential_provider_template; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credential_provider_template ON credential USING btree (provider_template) WHERE (provider_template IS NOT NULL);


--
-- Name: idx_credential_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credential_tenant ON credential USING btree (tenant_id);


--
-- Name: idx_credential_tenant_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_credential_tenant_type ON credential USING btree (tenant_id, type);


--
-- Name: idx_custom_domain_domain; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_domain_domain ON tenant_custom_domain USING btree (domain);


--
-- Name: idx_custom_rules_profile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_rules_profile ON profile_custom_rules USING btree (profile_id);


--
-- Name: idx_custom_rules_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_rules_tenant ON profile_custom_rules USING btree (tenant_id);


--
-- Name: idx_dashboard_component; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dashboard_component ON dashboard_component USING btree (dashboard_id, sort_order);


--
-- Name: idx_dashboard_component_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dashboard_component_tenant_id ON dashboard_component USING btree (tenant_id);


--
-- Name: idx_dashboard_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dashboard_tenant ON dashboard USING btree (tenant_id);


--
-- Name: idx_data_export_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_export_created_at ON data_export USING btree (created_at DESC);


--
-- Name: idx_data_export_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_export_status ON data_export USING btree (status);


--
-- Name: idx_data_export_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_data_export_tenant ON data_export USING btree (tenant_id);


--
-- Name: idx_delegated_admin_scope_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delegated_admin_scope_lookup ON delegated_admin_scope USING btree (tenant_id, active);


--
-- Name: idx_email_campaign_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_campaign_claim ON email_campaign USING btree (status, scheduled_at);


--
-- Name: idx_email_campaign_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_campaign_tenant ON email_campaign USING btree (tenant_id, created_at DESC);


--
-- Name: idx_email_log_template; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_log_template ON email_log USING btree (template_id);


--
-- Name: idx_email_log_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_log_tenant ON email_log USING btree (tenant_id, status);


--
-- Name: idx_email_template_smtp_credential; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_template_smtp_credential ON email_template USING btree (smtp_credential_id) WHERE (smtp_credential_id IS NOT NULL);


--
-- Name: idx_email_template_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_email_template_tenant ON email_template USING btree (tenant_id);


--
-- Name: idx_env_promotion_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_env_promotion_created ON environment_promotion USING btree (created_at DESC);


--
-- Name: idx_env_promotion_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_env_promotion_source ON environment_promotion USING btree (source_env_id);


--
-- Name: idx_env_promotion_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_env_promotion_status ON environment_promotion USING btree (status);


--
-- Name: idx_env_promotion_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_env_promotion_target ON environment_promotion USING btree (target_env_id);


--
-- Name: idx_env_promotion_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_env_promotion_tenant ON environment_promotion USING btree (tenant_id);


--
-- Name: idx_environment_sandbox_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_environment_sandbox_tenant ON environment USING btree (sandbox_tenant_id) WHERE (sandbox_tenant_id IS NOT NULL);


--
-- Name: idx_environment_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_environment_tenant ON environment USING btree (tenant_id);


--
-- Name: idx_environment_tenant_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_environment_tenant_name ON environment USING btree (tenant_id, name);


--
-- Name: idx_environment_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_environment_type ON environment USING btree (type);


--
-- Name: idx_field_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_active ON field USING btree (active);


--
-- Name: idx_field_collection_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_collection_active ON field USING btree (collection_id, active);


--
-- Name: idx_field_collection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_collection_id ON field USING btree (collection_id);


--
-- Name: idx_field_constraints_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_constraints_gin ON field USING gin (constraints jsonb_path_ops);


--
-- Name: idx_field_display_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_display_name ON field USING btree (display_name);


--
-- Name: idx_field_history_field; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_history_field ON field_history USING btree (collection_id, field_name, changed_at DESC);


--
-- Name: idx_field_history_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_history_record ON field_history USING btree (collection_id, record_id, changed_at DESC);


--
-- Name: idx_field_history_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_history_tenant ON field_history USING btree (tenant_id);


--
-- Name: idx_field_history_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_history_user ON field_history USING btree (changed_by, changed_at DESC);


--
-- Name: idx_field_indexed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_indexed ON field USING btree (indexed) WHERE (indexed = true);


--
-- Name: idx_field_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_name ON field USING btree (name);


--
-- Name: idx_field_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_order ON field USING btree (field_order);


--
-- Name: idx_field_reference_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_reference_collection ON field USING btree (reference_collection_id) WHERE (reference_collection_id IS NOT NULL);


--
-- Name: idx_field_reference_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_reference_target ON field USING btree (reference_target) WHERE (reference_target IS NOT NULL);


--
-- Name: idx_field_relationship_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_relationship_type ON field USING btree (relationship_type) WHERE (relationship_type IS NOT NULL);


--
-- Name: idx_field_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_type ON field USING btree (type);


--
-- Name: idx_field_unique; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_unique ON field USING btree (unique_constraint) WHERE (unique_constraint = true);


--
-- Name: idx_field_version_collection_version_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_version_collection_version_id ON field_version USING btree (collection_version_id);


--
-- Name: idx_field_version_field_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_version_field_id ON field_version USING btree (field_id);


--
-- Name: idx_flow_audit_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_audit_tenant ON flow_audit_log USING btree (tenant_id, flow_id);


--
-- Name: idx_flow_exec_flow; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_exec_flow ON flow_execution USING btree (flow_id);


--
-- Name: idx_flow_exec_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_exec_status ON flow_execution USING btree (tenant_id, status);


--
-- Name: idx_flow_execution_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_execution_status ON flow_execution USING btree (flow_id, status);


--
-- Name: idx_flow_pending_resume_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_pending_resume_event ON flow_pending_resume USING btree (resume_event) WHERE (claimed_by IS NULL);


--
-- Name: idx_flow_pending_resume_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_pending_resume_time ON flow_pending_resume USING btree (resume_at) WHERE (claimed_by IS NULL);


--
-- Name: idx_flow_step_log_exec; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_step_log_exec ON flow_step_log USING btree (execution_id);


--
-- Name: idx_flow_step_log_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_step_log_state ON flow_step_log USING btree (execution_id, state_id);


--
-- Name: idx_flow_step_log_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_step_log_time ON flow_step_log USING btree (execution_id, started_at);


--
-- Name: idx_flow_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_tenant ON flow USING btree (tenant_id);


--
-- Name: idx_flow_version_flow; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_flow_version_flow ON flow_version USING btree (flow_id, version_number DESC);


--
-- Name: idx_gm_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gm_group ON group_membership USING btree (group_id);


--
-- Name: idx_gm_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gm_member ON group_membership USING btree (member_type, member_id);


--
-- Name: idx_group_membership_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_group_membership_tenant_id ON group_membership USING btree (tenant_id);


--
-- Name: idx_job_exec_log; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_job_exec_log ON job_execution_log USING btree (job_id);


--
-- Name: idx_layout_assignment_resolve; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_layout_assignment_resolve ON layout_assignment USING btree (collection_id, evaluation_order);


--
-- Name: idx_layout_field; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_layout_field ON layout_field USING btree (section_id, sort_order);


--
-- Name: idx_layout_field_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_layout_field_tenant_id ON layout_field USING btree (tenant_id);


--
-- Name: idx_layout_related_list_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_layout_related_list_tenant_id ON layout_related_list USING btree (tenant_id);


--
-- Name: idx_layout_rule_layout; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_layout_rule_layout ON layout_rule USING btree (layout_id, active, sort_order);


--
-- Name: idx_layout_rule_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_layout_rule_tenant ON layout_rule USING btree (tenant_id);


--
-- Name: idx_layout_section_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_layout_section_tenant_id ON layout_section USING btree (tenant_id);


--
-- Name: idx_list_view_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_list_view_collection ON list_view USING btree (tenant_id, collection_id);


--
-- Name: idx_list_view_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_list_view_user ON list_view USING btree (created_by);


--
-- Name: idx_login_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_tenant ON login_history USING btree (tenant_id, login_time DESC);


--
-- Name: idx_login_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_user ON login_history USING btree (user_id, login_time DESC);


--
-- Name: idx_metadata_snapshot_env; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_metadata_snapshot_env ON metadata_snapshot USING btree (environment_id);


--
-- Name: idx_migration_run_collection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_run_collection_id ON migration_run USING btree (collection_id);


--
-- Name: idx_migration_run_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_run_created_at ON migration_run USING btree (created_at);


--
-- Name: idx_migration_run_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_run_status ON migration_run USING btree (status);


--
-- Name: idx_migration_run_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_run_tenant ON migration_run USING btree (tenant_id);


--
-- Name: idx_migration_step_details_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_step_details_gin ON migration_step USING gin (details jsonb_path_ops);


--
-- Name: idx_migration_step_migration_run_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_step_migration_run_id ON migration_step USING btree (migration_run_id);


--
-- Name: idx_migration_step_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_step_status ON migration_step USING btree (status);


--
-- Name: idx_migration_step_step_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_migration_step_step_number ON migration_step USING btree (step_number);


--
-- Name: idx_note_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_note_tenant ON note USING btree (tenant_id);


--
-- Name: idx_note_tenant_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_note_tenant_record ON note USING btree (tenant_id, collection_id, record_id, created_at DESC);


--
-- Name: idx_oidc_provider_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oidc_provider_active ON oidc_provider USING btree (active);


--
-- Name: idx_oidc_provider_issuer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oidc_provider_issuer ON oidc_provider USING btree (issuer);


--
-- Name: idx_oidc_provider_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oidc_provider_name ON oidc_provider USING btree (name);


--
-- Name: idx_oidc_provider_roles_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oidc_provider_roles_claim ON oidc_provider USING btree (roles_claim) WHERE (roles_claim IS NOT NULL);


--
-- Name: idx_oidc_provider_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oidc_provider_tenant ON oidc_provider USING btree (tenant_id);


--
-- Name: idx_package_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_created_at ON package USING btree (created_at);


--
-- Name: idx_package_history_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_history_created_at ON package_history USING btree (created_at DESC);


--
-- Name: idx_package_history_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_history_tenant_id ON package_history USING btree (tenant_id);


--
-- Name: idx_package_item_content_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_item_content_gin ON package_item USING gin (content jsonb_path_ops);


--
-- Name: idx_package_item_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_item_item_id ON package_item USING btree (item_id);


--
-- Name: idx_package_item_item_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_item_item_type ON package_item USING btree (item_type);


--
-- Name: idx_package_item_package_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_item_package_id ON package_item USING btree (package_id);


--
-- Name: idx_package_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_name ON package USING btree (name);


--
-- Name: idx_package_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_tenant ON package USING btree (tenant_id);


--
-- Name: idx_package_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_package_version ON package USING btree (version);


--
-- Name: idx_password_history_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_history_user ON password_history USING btree (user_id, created_at DESC);


--
-- Name: idx_picklist_dependency_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picklist_dependency_tenant_id ON picklist_dependency USING btree (tenant_id);


--
-- Name: idx_picklist_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picklist_source ON picklist_value USING btree (picklist_source_type, picklist_source_id);


--
-- Name: idx_picklist_value_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_picklist_value_tenant_id ON picklist_value USING btree (tenant_id);


--
-- Name: idx_platform_user_profile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_platform_user_profile ON platform_user USING btree (profile_id);


--
-- Name: idx_profile_field_perm_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_field_perm_collection ON profile_field_permission USING btree (collection_id);


--
-- Name: idx_profile_field_perm_profile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_field_perm_profile ON profile_field_permission USING btree (profile_id);


--
-- Name: idx_profile_field_permission_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_field_permission_tenant_id ON profile_field_permission USING btree (tenant_id);


--
-- Name: idx_profile_obj_perm_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_obj_perm_collection ON profile_object_permission USING btree (collection_id);


--
-- Name: idx_profile_obj_perm_profile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_obj_perm_profile ON profile_object_permission USING btree (profile_id);


--
-- Name: idx_profile_object_permission_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_object_permission_tenant_id ON profile_object_permission USING btree (tenant_id);


--
-- Name: idx_profile_sys_perm_profile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_sys_perm_profile ON profile_system_permission USING btree (profile_id);


--
-- Name: idx_profile_system_permission_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_system_permission_tenant_id ON profile_system_permission USING btree (tenant_id);


--
-- Name: idx_profile_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profile_tenant ON profile USING btree (tenant_id);


--
-- Name: idx_promotion_item_promo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotion_item_promo ON promotion_item USING btree (promotion_id);


--
-- Name: idx_promotion_item_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotion_item_tenant ON promotion_item USING btree (tenant_id);


--
-- Name: idx_push_device_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_push_device_user ON push_device USING btree (user_id, tenant_id);


--
-- Name: idx_quick_action_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_quick_action_lookup ON quick_action USING btree (tenant_id, collection_name, active, sort_order);


--
-- Name: idx_record_script_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_record_script_lookup ON record_script USING btree (tenant_id, collection_id, trigger_type, active, order_sequence);


--
-- Name: idx_record_share_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_record_share_lookup ON record_share USING btree (tenant_id, collection_id, record_id);


--
-- Name: idx_record_tombstone_changes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_record_tombstone_changes ON record_tombstone USING btree (tenant_id, collection_name, deleted_at);


--
-- Name: idx_record_type_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_record_type_collection ON record_type USING btree (collection_id, is_active);


--
-- Name: idx_record_type_picklist_rt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_record_type_picklist_rt ON record_type_picklist USING btree (record_type_id);


--
-- Name: idx_recovery_code_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_recovery_code_user ON user_recovery_code USING btree (user_id);


--
-- Name: idx_report_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_collection ON report USING btree (primary_collection_id);


--
-- Name: idx_report_folder; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_folder ON report USING btree (folder_id);


--
-- Name: idx_report_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_tenant ON report USING btree (tenant_id);


--
-- Name: idx_report_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_user ON report USING btree (created_by);


--
-- Name: idx_scheduled_job_next_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scheduled_job_next_run ON scheduled_job USING btree (active, next_run_at);


--
-- Name: idx_scheduled_job_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scheduled_job_tenant ON scheduled_job USING btree (tenant_id);


--
-- Name: idx_scim_client_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scim_client_tenant ON scim_client USING btree (tenant_id);


--
-- Name: idx_scim_client_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scim_client_token_hash ON scim_client USING btree (token_hash);


--
-- Name: idx_script_exec_log; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_script_exec_log ON script_execution_log USING btree (tenant_id, script_id, executed_at DESC);


--
-- Name: idx_script_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_script_tenant ON script USING btree (tenant_id);


--
-- Name: idx_script_trigger_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_script_trigger_collection ON script_trigger USING btree (collection_id, trigger_event);


--
-- Name: idx_script_trigger_script; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_script_trigger_script ON script_trigger USING btree (script_id);


--
-- Name: idx_search_index_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_search_index_tenant ON search_index USING btree (tenant_id);


--
-- Name: idx_search_index_vector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_search_index_vector ON search_index USING gin (search_vector);


--
-- Name: idx_section_layout; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_section_layout ON layout_section USING btree (layout_id, sort_order);


--
-- Name: idx_security_audit_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_audit_actor ON security_audit_log USING btree (actor_user_id, created_at DESC);


--
-- Name: idx_security_audit_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_audit_category ON security_audit_log USING btree (event_category, created_at DESC);


--
-- Name: idx_security_audit_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_audit_event_type ON security_audit_log USING btree (event_type, created_at DESC);


--
-- Name: idx_security_audit_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_audit_target ON security_audit_log USING btree (target_type, target_id, created_at DESC);


--
-- Name: idx_security_audit_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_audit_tenant_time ON security_audit_log USING btree (tenant_id, created_at DESC);


--
-- Name: idx_sms_verification_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sms_verification_phone ON sms_verification USING btree (phone, tenant_id, created_at DESC);


--
-- Name: idx_tenant_cell_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_cell_id ON tenant USING btree (cell_id);


--
-- Name: idx_tenant_custom_domain_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_custom_domain_token ON tenant_custom_domain USING btree (verification_token) WHERE (verification_token IS NOT NULL);


--
-- Name: idx_tenant_email_smtp_credential; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_email_smtp_credential ON tenant USING btree (email_smtp_credential_id) WHERE (email_smtp_credential_id IS NOT NULL);


--
-- Name: idx_tenant_module_action_module; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_module_action_module ON tenant_module_action USING btree (tenant_module_id);


--
-- Name: idx_tenant_module_action_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_module_action_tenant_id ON tenant_module_action USING btree (tenant_id);


--
-- Name: idx_tenant_module_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_module_status ON tenant_module USING btree (tenant_id, status);


--
-- Name: idx_tenant_module_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_module_tenant ON tenant_module USING btree (tenant_id);


--
-- Name: idx_tenant_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_parent ON tenant USING btree (parent_tenant_id) WHERE (parent_tenant_id IS NOT NULL);


--
-- Name: idx_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_status ON tenant USING btree (status);


--
-- Name: idx_token_app; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_token_app ON connected_app_token USING btree (connected_app_id);


--
-- Name: idx_token_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_token_expires ON connected_app_token USING btree (expires_at) WHERE (revoked = false);


--
-- Name: idx_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_token_hash ON connected_app_token USING btree (token_hash);


--
-- Name: idx_ui_menu_item_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_menu_item_active ON ui_menu_item USING btree (active);


--
-- Name: idx_ui_menu_item_display_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_menu_item_display_order ON ui_menu_item USING btree (display_order);


--
-- Name: idx_ui_menu_item_menu_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_menu_item_menu_id ON ui_menu_item USING btree (menu_id);


--
-- Name: idx_ui_menu_item_menu_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_menu_item_menu_order ON ui_menu_item USING btree (menu_id, display_order);


--
-- Name: idx_ui_menu_item_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_menu_item_tenant_id ON ui_menu_item USING btree (tenant_id);


--
-- Name: idx_ui_menu_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_menu_name ON ui_menu USING btree (name);


--
-- Name: idx_ui_menu_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_menu_tenant ON ui_menu USING btree (tenant_id);


--
-- Name: idx_ui_page_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_page_active ON ui_page USING btree (active);


--
-- Name: idx_ui_page_config_gin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_page_config_gin ON ui_page USING gin (config jsonb_path_ops);


--
-- Name: idx_ui_page_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_page_name ON ui_page USING btree (name);


--
-- Name: idx_ui_page_path; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_page_path ON ui_page USING btree (path);


--
-- Name: idx_ui_page_published; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_page_published ON ui_page USING btree (published);


--
-- Name: idx_ui_page_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_page_slug ON ui_page USING btree (slug);


--
-- Name: idx_ui_page_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ui_page_tenant ON ui_page USING btree (tenant_id);


--
-- Name: idx_user_api_token_hash_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_api_token_hash_active ON user_api_token USING btree (token_hash) WHERE (revoked = false);


--
-- Name: idx_user_api_token_prefix_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_api_token_prefix_active ON user_api_token USING btree (token_prefix) WHERE (revoked = false);


--
-- Name: idx_user_api_token_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_api_token_tenant ON user_api_token USING btree (tenant_id);


--
-- Name: idx_user_api_token_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_api_token_user ON user_api_token USING btree (user_id);


--
-- Name: idx_user_credential_reset_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_credential_reset_token ON user_credential USING btree (reset_token) WHERE (reset_token IS NOT NULL);


--
-- Name: idx_user_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_email ON platform_user USING btree (tenant_id, email);


--
-- Name: idx_user_group_oidc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_group_oidc ON user_group USING btree (tenant_id, oidc_group_name) WHERE (oidc_group_name IS NOT NULL);


--
-- Name: idx_user_group_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_group_source ON user_group USING btree (tenant_id, source);


--
-- Name: idx_user_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_manager ON platform_user USING btree (manager_id);


--
-- Name: idx_user_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_status ON platform_user USING btree (tenant_id, status);


--
-- Name: idx_user_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_tenant ON platform_user USING btree (tenant_id);


--
-- Name: idx_validation_rule_collection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_validation_rule_collection ON validation_rule USING btree (collection_id, active);


--
-- Name: idx_validation_rule_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_validation_rule_tenant ON validation_rule USING btree (tenant_id);


--
-- Name: uq_email_template_tenant_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_email_template_tenant_key ON email_template USING btree (tenant_id, template_key) WHERE (template_key IS NOT NULL);


--
-- Name: uq_platform_user_email_verification_token; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_platform_user_email_verification_token ON platform_user USING btree (email_verification_token) WHERE (email_verification_token IS NOT NULL);


--
-- Name: search_index trig_search_index_vector; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trig_search_index_vector BEFORE INSERT OR UPDATE OF search_content ON search_index FOR EACH ROW EXECUTE FUNCTION search_index_update_vector();


--
-- Name: api_operation api_operation_spec_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_operation
    ADD CONSTRAINT api_operation_spec_id_fkey FOREIGN KEY (spec_id) REFERENCES api_spec(id) ON DELETE CASCADE;


--
-- Name: api_spec api_spec_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_spec
    ADD CONSTRAINT api_spec_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: approval_instance approval_instance_approval_process_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_instance
    ADD CONSTRAINT approval_instance_approval_process_id_fkey FOREIGN KEY (approval_process_id) REFERENCES approval_process(id);


--
-- Name: approval_instance approval_instance_submitted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_instance
    ADD CONSTRAINT approval_instance_submitted_by_fkey FOREIGN KEY (submitted_by) REFERENCES platform_user(id);


--
-- Name: approval_process approval_process_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_process
    ADD CONSTRAINT approval_process_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: approval_process approval_process_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_process
    ADD CONSTRAINT approval_process_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: approval_step approval_step_approval_process_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_step
    ADD CONSTRAINT approval_step_approval_process_id_fkey FOREIGN KEY (approval_process_id) REFERENCES approval_process(id) ON DELETE CASCADE;


--
-- Name: approval_step_instance approval_step_instance_approval_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_step_instance
    ADD CONSTRAINT approval_step_instance_approval_instance_id_fkey FOREIGN KEY (approval_instance_id) REFERENCES approval_instance(id) ON DELETE CASCADE;


--
-- Name: approval_step_instance approval_step_instance_assigned_to_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_step_instance
    ADD CONSTRAINT approval_step_instance_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES platform_user(id);


--
-- Name: approval_step_instance approval_step_instance_step_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY approval_step_instance
    ADD CONSTRAINT approval_step_instance_step_id_fkey FOREIGN KEY (step_id) REFERENCES approval_step(id);


--
-- Name: bulk_job bulk_job_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY bulk_job
    ADD CONSTRAINT bulk_job_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: bulk_job_result bulk_job_result_bulk_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY bulk_job_result
    ADD CONSTRAINT bulk_job_result_bulk_job_id_fkey FOREIGN KEY (bulk_job_id) REFERENCES bulk_job(id) ON DELETE CASCADE;


--
-- Name: bulk_job bulk_job_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY bulk_job
    ADD CONSTRAINT bulk_job_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: connected_app_audit connected_app_audit_connected_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app_audit
    ADD CONSTRAINT connected_app_audit_connected_app_id_fkey FOREIGN KEY (connected_app_id) REFERENCES connected_app(id) ON DELETE CASCADE;


--
-- Name: connected_app connected_app_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app
    ADD CONSTRAINT connected_app_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: connected_app_token connected_app_token_connected_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app_token
    ADD CONSTRAINT connected_app_token_connected_app_id_fkey FOREIGN KEY (connected_app_id) REFERENCES connected_app(id) ON DELETE CASCADE;


--
-- Name: credential_oauth_token credential_oauth_token_credential_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY credential_oauth_token
    ADD CONSTRAINT credential_oauth_token_credential_id_fkey FOREIGN KEY (credential_id) REFERENCES credential(id) ON DELETE CASCADE;


--
-- Name: credential_oauth_token credential_oauth_token_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY credential_oauth_token
    ADD CONSTRAINT credential_oauth_token_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: credential credential_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY credential
    ADD CONSTRAINT credential_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: dashboard_component dashboard_component_dashboard_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard_component
    ADD CONSTRAINT dashboard_component_dashboard_id_fkey FOREIGN KEY (dashboard_id) REFERENCES dashboard(id) ON DELETE CASCADE;


--
-- Name: dashboard_component dashboard_component_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard_component
    ADD CONSTRAINT dashboard_component_report_id_fkey FOREIGN KEY (report_id) REFERENCES report(id);


--
-- Name: dashboard dashboard_folder_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard
    ADD CONSTRAINT dashboard_folder_id_fkey FOREIGN KEY (folder_id) REFERENCES report_folder(id);


--
-- Name: dashboard dashboard_running_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard
    ADD CONSTRAINT dashboard_running_user_id_fkey FOREIGN KEY (running_user_id) REFERENCES platform_user(id);


--
-- Name: dashboard dashboard_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard
    ADD CONSTRAINT dashboard_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: data_export data_export_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_export
    ADD CONSTRAINT data_export_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: email_campaign_recipient email_campaign_recipient_campaign_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_campaign_recipient
    ADD CONSTRAINT email_campaign_recipient_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES email_campaign(id) ON DELETE CASCADE;


--
-- Name: email_campaign_recipient email_campaign_recipient_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_campaign_recipient
    ADD CONSTRAINT email_campaign_recipient_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: email_campaign email_campaign_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_campaign
    ADD CONSTRAINT email_campaign_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: email_log email_log_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_log
    ADD CONSTRAINT email_log_template_id_fkey FOREIGN KEY (template_id) REFERENCES email_template(id);


--
-- Name: email_suppression email_suppression_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_suppression
    ADD CONSTRAINT email_suppression_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: email_template email_template_related_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_template
    ADD CONSTRAINT email_template_related_collection_id_fkey FOREIGN KEY (related_collection_id) REFERENCES collection(id);


--
-- Name: email_template email_template_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_template
    ADD CONSTRAINT email_template_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: environment_promotion environment_promotion_snapshot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment_promotion
    ADD CONSTRAINT environment_promotion_snapshot_id_fkey FOREIGN KEY (snapshot_id) REFERENCES metadata_snapshot(id);


--
-- Name: environment_promotion environment_promotion_source_env_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment_promotion
    ADD CONSTRAINT environment_promotion_source_env_id_fkey FOREIGN KEY (source_env_id) REFERENCES environment(id);


--
-- Name: environment_promotion environment_promotion_target_env_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment_promotion
    ADD CONSTRAINT environment_promotion_target_env_id_fkey FOREIGN KEY (target_env_id) REFERENCES environment(id);


--
-- Name: environment_promotion environment_promotion_target_snapshot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment_promotion
    ADD CONSTRAINT environment_promotion_target_snapshot_id_fkey FOREIGN KEY (target_snapshot_id) REFERENCES metadata_snapshot(id);


--
-- Name: environment_promotion environment_promotion_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment_promotion
    ADD CONSTRAINT environment_promotion_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: environment environment_sandbox_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment
    ADD CONSTRAINT environment_sandbox_tenant_id_fkey FOREIGN KEY (sandbox_tenant_id) REFERENCES tenant(id);


--
-- Name: environment environment_source_env_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment
    ADD CONSTRAINT environment_source_env_id_fkey FOREIGN KEY (source_env_id) REFERENCES environment(id);


--
-- Name: environment environment_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY environment
    ADD CONSTRAINT environment_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: field_history field_history_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_history
    ADD CONSTRAINT field_history_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: field_history field_history_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_history
    ADD CONSTRAINT field_history_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: file_attachment file_attachment_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY file_attachment
    ADD CONSTRAINT file_attachment_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: file_attachment file_attachment_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY file_attachment
    ADD CONSTRAINT file_attachment_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: collection fk_collection_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection
    ADD CONSTRAINT fk_collection_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: collection fk_collection_display_field; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection
    ADD CONSTRAINT fk_collection_display_field FOREIGN KEY (display_field_id) REFERENCES field(id) ON DELETE SET NULL;


--
-- Name: collection fk_collection_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection
    ADD CONSTRAINT fk_collection_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: collection fk_collection_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection
    ADD CONSTRAINT fk_collection_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: collection_version fk_collection_version_collection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection_version
    ADD CONSTRAINT fk_collection_version_collection FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;


--
-- Name: collection_version fk_collection_version_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection_version
    ADD CONSTRAINT fk_collection_version_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: collection_version fk_collection_version_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY collection_version
    ADD CONSTRAINT fk_collection_version_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: connected_app fk_connected_app_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app
    ADD CONSTRAINT fk_connected_app_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: connected_app fk_connected_app_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY connected_app
    ADD CONSTRAINT fk_connected_app_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: profile_custom_rules fk_custom_rule_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_custom_rules
    ADD CONSTRAINT fk_custom_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: dashboard_component fk_dashboard_component_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard_component
    ADD CONSTRAINT fk_dashboard_component_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: dashboard fk_dashboard_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard
    ADD CONSTRAINT fk_dashboard_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: dashboard fk_dashboard_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY dashboard
    ADD CONSTRAINT fk_dashboard_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: email_template fk_email_template_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_template
    ADD CONSTRAINT fk_email_template_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: email_template fk_email_template_smtp_credential; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_template
    ADD CONSTRAINT fk_email_template_smtp_credential FOREIGN KEY (smtp_credential_id) REFERENCES credential(id) ON DELETE SET NULL;


--
-- Name: email_template fk_email_template_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY email_template
    ADD CONSTRAINT fk_email_template_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: field fk_field_collection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field
    ADD CONSTRAINT fk_field_collection FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;


--
-- Name: field fk_field_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field
    ADD CONSTRAINT fk_field_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: field fk_field_reference_collection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field
    ADD CONSTRAINT fk_field_reference_collection FOREIGN KEY (reference_collection_id) REFERENCES collection(id) ON DELETE SET NULL;


--
-- Name: field fk_field_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field
    ADD CONSTRAINT fk_field_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: field_version fk_field_version_collection_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_version
    ADD CONSTRAINT fk_field_version_collection_version FOREIGN KEY (collection_version_id) REFERENCES collection_version(id) ON DELETE CASCADE;


--
-- Name: field_version fk_field_version_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_version
    ADD CONSTRAINT fk_field_version_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: field_version fk_field_version_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_version
    ADD CONSTRAINT fk_field_version_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: file_attachment fk_file_attachment_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY file_attachment
    ADD CONSTRAINT fk_file_attachment_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: file_attachment fk_file_attachment_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY file_attachment
    ADD CONSTRAINT fk_file_attachment_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: flow fk_flow_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow
    ADD CONSTRAINT fk_flow_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: flow fk_flow_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow
    ADD CONSTRAINT fk_flow_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: global_picklist fk_global_picklist_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_picklist
    ADD CONSTRAINT fk_global_picklist_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: global_picklist fk_global_picklist_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_picklist
    ADD CONSTRAINT fk_global_picklist_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: group_membership fk_group_membership_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY group_membership
    ADD CONSTRAINT fk_group_membership_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: layout_field fk_layout_field_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_field
    ADD CONSTRAINT fk_layout_field_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: layout_related_list fk_layout_related_list_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_related_list
    ADD CONSTRAINT fk_layout_related_list_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: layout_section fk_layout_section_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_section
    ADD CONSTRAINT fk_layout_section_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: list_view fk_list_view_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY list_view
    ADD CONSTRAINT fk_list_view_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: list_view fk_list_view_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY list_view
    ADD CONSTRAINT fk_list_view_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: migration_run fk_migration_run_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY migration_run
    ADD CONSTRAINT fk_migration_run_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: migration_step fk_migration_step_migration_run; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY migration_step
    ADD CONSTRAINT fk_migration_step_migration_run FOREIGN KEY (migration_run_id) REFERENCES migration_run(id) ON DELETE CASCADE;


--
-- Name: note fk_note_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY note
    ADD CONSTRAINT fk_note_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: note fk_note_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY note
    ADD CONSTRAINT fk_note_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: oidc_provider fk_oidc_provider_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY oidc_provider
    ADD CONSTRAINT fk_oidc_provider_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: package_history fk_package_history_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY package_history
    ADD CONSTRAINT fk_package_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: package_item fk_package_item_package; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY package_item
    ADD CONSTRAINT fk_package_item_package FOREIGN KEY (package_id) REFERENCES package(id) ON DELETE CASCADE;


--
-- Name: package fk_package_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY package
    ADD CONSTRAINT fk_package_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: page_layout fk_page_layout_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY page_layout
    ADD CONSTRAINT fk_page_layout_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: page_layout fk_page_layout_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY page_layout
    ADD CONSTRAINT fk_page_layout_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: picklist_dependency fk_picklist_dependency_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_dependency
    ADD CONSTRAINT fk_picklist_dependency_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: picklist_value fk_picklist_value_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_value
    ADD CONSTRAINT fk_picklist_value_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: profile fk_profile_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile
    ADD CONSTRAINT fk_profile_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: profile_field_permission fk_profile_field_perm_collection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_field_permission
    ADD CONSTRAINT fk_profile_field_perm_collection FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;


--
-- Name: profile_field_permission fk_profile_field_perm_field; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_field_permission
    ADD CONSTRAINT fk_profile_field_perm_field FOREIGN KEY (field_id) REFERENCES field(id) ON DELETE CASCADE;


--
-- Name: profile_field_permission fk_profile_field_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_field_permission
    ADD CONSTRAINT fk_profile_field_permission_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: profile_object_permission fk_profile_obj_perm_collection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_object_permission
    ADD CONSTRAINT fk_profile_obj_perm_collection FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;


--
-- Name: profile_object_permission fk_profile_object_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_object_permission
    ADD CONSTRAINT fk_profile_object_permission_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: profile_system_permission fk_profile_system_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_system_permission
    ADD CONSTRAINT fk_profile_system_permission_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: profile fk_profile_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile
    ADD CONSTRAINT fk_profile_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: record_type fk_record_type_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type
    ADD CONSTRAINT fk_record_type_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: record_type fk_record_type_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type
    ADD CONSTRAINT fk_record_type_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: report fk_report_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report
    ADD CONSTRAINT fk_report_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: report_folder fk_report_folder_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report_folder
    ADD CONSTRAINT fk_report_folder_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: report_folder fk_report_folder_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report_folder
    ADD CONSTRAINT fk_report_folder_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: report fk_report_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report
    ADD CONSTRAINT fk_report_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: scheduled_job fk_scheduled_job_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scheduled_job
    ADD CONSTRAINT fk_scheduled_job_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: scheduled_job fk_scheduled_job_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scheduled_job
    ADD CONSTRAINT fk_scheduled_job_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: script fk_script_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script
    ADD CONSTRAINT fk_script_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: script fk_script_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script
    ADD CONSTRAINT fk_script_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: tenant_module_action fk_tenant_module_action_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_module_action
    ADD CONSTRAINT fk_tenant_module_action_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: ui_menu_item fk_ui_menu_item_menu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu_item
    ADD CONSTRAINT fk_ui_menu_item_menu FOREIGN KEY (menu_id) REFERENCES ui_menu(id) ON DELETE CASCADE;


--
-- Name: ui_menu_item fk_ui_menu_item_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu_item
    ADD CONSTRAINT fk_ui_menu_item_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: ui_menu fk_ui_menu_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu
    ADD CONSTRAINT fk_ui_menu_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: ui_page fk_ui_page_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_page
    ADD CONSTRAINT fk_ui_page_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: ui_page fk_ui_page_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_page
    ADD CONSTRAINT fk_ui_page_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: ui_page fk_ui_page_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_page
    ADD CONSTRAINT fk_ui_page_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: user_credential fk_user_credential_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_credential
    ADD CONSTRAINT fk_user_credential_user FOREIGN KEY (user_id) REFERENCES platform_user(id) ON DELETE CASCADE;


--
-- Name: user_group fk_user_group_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group
    ADD CONSTRAINT fk_user_group_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: user_group fk_user_group_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group
    ADD CONSTRAINT fk_user_group_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: validation_rule fk_validation_rule_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY validation_rule
    ADD CONSTRAINT fk_validation_rule_created_by FOREIGN KEY (created_by) REFERENCES platform_user(id);


--
-- Name: validation_rule fk_validation_rule_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY validation_rule
    ADD CONSTRAINT fk_validation_rule_updated_by FOREIGN KEY (updated_by) REFERENCES platform_user(id);


--
-- Name: flow_execution flow_execution_flow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_execution
    ADD CONSTRAINT flow_execution_flow_id_fkey FOREIGN KEY (flow_id) REFERENCES flow(id);


--
-- Name: flow_pending_resume flow_pending_resume_execution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_pending_resume
    ADD CONSTRAINT flow_pending_resume_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES flow_execution(id) ON DELETE CASCADE;


--
-- Name: flow_step_log flow_step_log_execution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_step_log
    ADD CONSTRAINT flow_step_log_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES flow_execution(id) ON DELETE CASCADE;


--
-- Name: flow flow_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow
    ADD CONSTRAINT flow_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: flow_version flow_version_flow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY flow_version
    ADD CONSTRAINT flow_version_flow_id_fkey FOREIGN KEY (flow_id) REFERENCES flow(id) ON DELETE CASCADE;


--
-- Name: global_picklist global_picklist_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY global_picklist
    ADD CONSTRAINT global_picklist_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: group_membership group_membership_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY group_membership
    ADD CONSTRAINT group_membership_group_id_fkey FOREIGN KEY (group_id) REFERENCES user_group(id) ON DELETE CASCADE;


--
-- Name: job_execution_log job_execution_log_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY job_execution_log
    ADD CONSTRAINT job_execution_log_job_id_fkey FOREIGN KEY (job_id) REFERENCES scheduled_job(id);


--
-- Name: layout_assignment layout_assignment_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_assignment
    ADD CONSTRAINT layout_assignment_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: layout_assignment layout_assignment_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_assignment
    ADD CONSTRAINT layout_assignment_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES page_layout(id);


--
-- Name: layout_assignment layout_assignment_record_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_assignment
    ADD CONSTRAINT layout_assignment_record_type_id_fkey FOREIGN KEY (record_type_id) REFERENCES record_type(id);


--
-- Name: layout_assignment layout_assignment_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_assignment
    ADD CONSTRAINT layout_assignment_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: layout_field layout_field_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_field
    ADD CONSTRAINT layout_field_field_id_fkey FOREIGN KEY (field_id) REFERENCES field(id);


--
-- Name: layout_field layout_field_section_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_field
    ADD CONSTRAINT layout_field_section_id_fkey FOREIGN KEY (section_id) REFERENCES layout_section(id) ON DELETE CASCADE;


--
-- Name: layout_related_list layout_related_list_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_related_list
    ADD CONSTRAINT layout_related_list_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES page_layout(id) ON DELETE CASCADE;


--
-- Name: layout_related_list layout_related_list_related_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_related_list
    ADD CONSTRAINT layout_related_list_related_collection_id_fkey FOREIGN KEY (related_collection_id) REFERENCES collection(id);


--
-- Name: layout_related_list layout_related_list_relationship_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_related_list
    ADD CONSTRAINT layout_related_list_relationship_field_id_fkey FOREIGN KEY (relationship_field_id) REFERENCES field(id);


--
-- Name: layout_rule layout_rule_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_rule
    ADD CONSTRAINT layout_rule_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES page_layout(id) ON DELETE CASCADE;


--
-- Name: layout_rule layout_rule_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_rule
    ADD CONSTRAINT layout_rule_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: layout_section layout_section_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layout_section
    ADD CONSTRAINT layout_section_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES page_layout(id) ON DELETE CASCADE;


--
-- Name: list_view list_view_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY list_view
    ADD CONSTRAINT list_view_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: list_view list_view_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY list_view
    ADD CONSTRAINT list_view_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: login_history login_history_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY login_history
    ADD CONSTRAINT login_history_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: login_history login_history_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY login_history
    ADD CONSTRAINT login_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform_user(id);


--
-- Name: metadata_snapshot metadata_snapshot_environment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY metadata_snapshot
    ADD CONSTRAINT metadata_snapshot_environment_id_fkey FOREIGN KEY (environment_id) REFERENCES environment(id);


--
-- Name: metadata_snapshot metadata_snapshot_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY metadata_snapshot
    ADD CONSTRAINT metadata_snapshot_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: note note_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY note
    ADD CONSTRAINT note_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: note note_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY note
    ADD CONSTRAINT note_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: observability_settings observability_settings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY observability_settings
    ADD CONSTRAINT observability_settings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: page_layout page_layout_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY page_layout
    ADD CONSTRAINT page_layout_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: page_layout page_layout_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY page_layout
    ADD CONSTRAINT page_layout_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: password_policy password_policy_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY password_policy
    ADD CONSTRAINT password_policy_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: picklist_dependency picklist_dependency_controlling_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_dependency
    ADD CONSTRAINT picklist_dependency_controlling_field_id_fkey FOREIGN KEY (controlling_field_id) REFERENCES field(id);


--
-- Name: picklist_dependency picklist_dependency_dependent_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY picklist_dependency
    ADD CONSTRAINT picklist_dependency_dependent_field_id_fkey FOREIGN KEY (dependent_field_id) REFERENCES field(id);


--
-- Name: platform_user platform_user_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY platform_user
    ADD CONSTRAINT platform_user_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES platform_user(id);


--
-- Name: platform_user platform_user_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY platform_user
    ADD CONSTRAINT platform_user_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES profile(id);


--
-- Name: platform_user platform_user_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY platform_user
    ADD CONSTRAINT platform_user_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: profile_custom_rules profile_custom_rules_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_custom_rules
    ADD CONSTRAINT profile_custom_rules_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE;


--
-- Name: profile_field_permission profile_field_permission_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_field_permission
    ADD CONSTRAINT profile_field_permission_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE;


--
-- Name: profile_object_permission profile_object_permission_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_object_permission
    ADD CONSTRAINT profile_object_permission_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE;


--
-- Name: profile_system_permission profile_system_permission_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile_system_permission
    ADD CONSTRAINT profile_system_permission_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE;


--
-- Name: profile profile_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY profile
    ADD CONSTRAINT profile_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: promotion_item promotion_item_promotion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY promotion_item
    ADD CONSTRAINT promotion_item_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES environment_promotion(id) ON DELETE CASCADE;


--
-- Name: push_device push_device_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY push_device
    ADD CONSTRAINT push_device_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform_user(id);


--
-- Name: record_type record_type_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type
    ADD CONSTRAINT record_type_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: record_type_picklist record_type_picklist_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type_picklist
    ADD CONSTRAINT record_type_picklist_field_id_fkey FOREIGN KEY (field_id) REFERENCES field(id);


--
-- Name: record_type_picklist record_type_picklist_record_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type_picklist
    ADD CONSTRAINT record_type_picklist_record_type_id_fkey FOREIGN KEY (record_type_id) REFERENCES record_type(id) ON DELETE CASCADE;


--
-- Name: record_type record_type_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY record_type
    ADD CONSTRAINT record_type_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: report report_folder_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report
    ADD CONSTRAINT report_folder_id_fkey FOREIGN KEY (folder_id) REFERENCES report_folder(id);


--
-- Name: report_folder report_folder_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report_folder
    ADD CONSTRAINT report_folder_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: report report_primary_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report
    ADD CONSTRAINT report_primary_collection_id_fkey FOREIGN KEY (primary_collection_id) REFERENCES collection(id);


--
-- Name: report report_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY report
    ADD CONSTRAINT report_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: scheduled_job scheduled_job_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scheduled_job
    ADD CONSTRAINT scheduled_job_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: scim_client scim_client_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY scim_client
    ADD CONSTRAINT scim_client_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: script_execution_log script_execution_log_script_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script_execution_log
    ADD CONSTRAINT script_execution_log_script_id_fkey FOREIGN KEY (script_id) REFERENCES script(id);


--
-- Name: script script_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script
    ADD CONSTRAINT script_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: script_trigger script_trigger_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script_trigger
    ADD CONSTRAINT script_trigger_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: script_trigger script_trigger_script_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY script_trigger
    ADD CONSTRAINT script_trigger_script_id_fkey FOREIGN KEY (script_id) REFERENCES script(id) ON DELETE CASCADE;


--
-- Name: search_index search_index_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY search_index
    ADD CONSTRAINT search_index_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;


--
-- Name: security_audit_log security_audit_log_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY security_audit_log
    ADD CONSTRAINT security_audit_log_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: setup_audit_trail setup_audit_trail_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY setup_audit_trail
    ADD CONSTRAINT setup_audit_trail_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: setup_audit_trail setup_audit_trail_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY setup_audit_trail
    ADD CONSTRAINT setup_audit_trail_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform_user(id);


--
-- Name: tenant_custom_domain tenant_custom_domain_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_custom_domain
    ADD CONSTRAINT tenant_custom_domain_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: tenant tenant_email_smtp_credential_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant
    ADD CONSTRAINT tenant_email_smtp_credential_id_fkey FOREIGN KEY (email_smtp_credential_id) REFERENCES credential(id) ON DELETE SET NULL;


--
-- Name: tenant_module_action tenant_module_action_tenant_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_module_action
    ADD CONSTRAINT tenant_module_action_tenant_module_id_fkey FOREIGN KEY (tenant_module_id) REFERENCES tenant_module(id) ON DELETE CASCADE;


--
-- Name: tenant_module tenant_module_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant_module
    ADD CONSTRAINT tenant_module_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: tenant tenant_parent_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tenant
    ADD CONSTRAINT tenant_parent_tenant_id_fkey FOREIGN KEY (parent_tenant_id) REFERENCES tenant(id);


--
-- Name: user_api_token user_api_token_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_api_token
    ADD CONSTRAINT user_api_token_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: user_api_token user_api_token_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_api_token
    ADD CONSTRAINT user_api_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform_user(id) ON DELETE CASCADE;


--
-- Name: user_group user_group_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group
    ADD CONSTRAINT user_group_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: user_recovery_code user_recovery_code_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_recovery_code
    ADD CONSTRAINT user_recovery_code_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform_user(id);


--
-- Name: user_totp_secret user_totp_secret_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_totp_secret
    ADD CONSTRAINT user_totp_secret_user_id_fkey FOREIGN KEY (user_id) REFERENCES platform_user(id);


--
-- Name: validation_rule validation_rule_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY validation_rule
    ADD CONSTRAINT validation_rule_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES collection(id);


--
-- Name: validation_rule validation_rule_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY validation_rule
    ADD CONSTRAINT validation_rule_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenant(id);


--
-- Name: api_call_idempotency admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON api_call_idempotency USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: api_operation admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON api_operation USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: api_spec admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON api_spec USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: approval_instance admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON approval_instance USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: approval_process admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON approval_process USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: bulk_job admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON bulk_job USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: collection admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON collection USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: connected_app admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON connected_app USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: credential admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON credential USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: credential_oauth_token admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON credential_oauth_token USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: dashboard admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON dashboard USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: dashboard_component admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON dashboard_component USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: delegated_admin_scope admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON delegated_admin_scope USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: email_campaign admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON email_campaign USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: email_campaign_recipient admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON email_campaign_recipient USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: email_log admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON email_log USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: email_suppression admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON email_suppression USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: email_template admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON email_template USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: environment admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON environment USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: environment_promotion admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON environment_promotion USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: field_history admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON field_history USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: file_attachment admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON file_attachment USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: flow admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON flow USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: flow_execution admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON flow_execution USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: global_picklist admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON global_picklist USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: group_membership admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON group_membership USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: layout_field admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON layout_field USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: layout_related_list admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON layout_related_list USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: layout_rule admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON layout_rule USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: layout_section admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON layout_section USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: list_view admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON list_view USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: login_history admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON login_history USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: metadata_snapshot admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON metadata_snapshot USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: migration_run admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON migration_run USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: note admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON note USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: oidc_provider admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON oidc_provider USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: package admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON package USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: page_layout admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON page_layout USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: picklist_dependency admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON picklist_dependency USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: picklist_value admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON picklist_value USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: platform_user admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON platform_user USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: profile admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON profile USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: profile_field_permission admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON profile_field_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: profile_object_permission admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON profile_object_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: profile_system_permission admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON profile_system_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: promotion_item admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON promotion_item USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: quick_action admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON quick_action USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: record_script admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON record_script USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: record_share admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON record_share USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: record_tombstone admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON record_tombstone USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: record_type admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON record_type USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: report admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON report USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: report_folder admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON report_folder USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: saml_provider admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON saml_provider USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: scheduled_job admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON scheduled_job USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: script admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON script USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: search_index admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON search_index USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: security_audit_log admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON security_audit_log USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: setup_audit_trail admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON setup_audit_trail USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: tenant_module_action admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON tenant_module_action USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: tenant_otlp_target admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON tenant_otlp_target USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: ui_menu admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON ui_menu USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: ui_menu_item admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON ui_menu_item USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: ui_page admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON ui_page USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: user_group admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON user_group USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: validation_rule admin_bypass; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY admin_bypass ON validation_rule USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));


--
-- Name: api_call_idempotency; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE api_call_idempotency ENABLE ROW LEVEL SECURITY;

--
-- Name: api_operation; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE api_operation ENABLE ROW LEVEL SECURITY;

--
-- Name: api_spec; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE api_spec ENABLE ROW LEVEL SECURITY;

--
-- Name: approval_instance; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE approval_instance ENABLE ROW LEVEL SECURITY;

--
-- Name: approval_process; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE approval_process ENABLE ROW LEVEL SECURITY;

--
-- Name: bulk_job; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE bulk_job ENABLE ROW LEVEL SECURITY;

--
-- Name: collection; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE collection ENABLE ROW LEVEL SECURITY;

--
-- Name: connected_app; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE connected_app ENABLE ROW LEVEL SECURITY;

--
-- Name: credential; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE credential ENABLE ROW LEVEL SECURITY;

--
-- Name: credential_oauth_token; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE credential_oauth_token ENABLE ROW LEVEL SECURITY;

--
-- Name: dashboard; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE dashboard ENABLE ROW LEVEL SECURITY;

--
-- Name: dashboard_component; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE dashboard_component ENABLE ROW LEVEL SECURITY;

--
-- Name: delegated_admin_scope; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE delegated_admin_scope ENABLE ROW LEVEL SECURITY;

--
-- Name: email_campaign; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE email_campaign ENABLE ROW LEVEL SECURITY;

--
-- Name: email_campaign_recipient; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE email_campaign_recipient ENABLE ROW LEVEL SECURITY;

--
-- Name: email_log; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE email_log ENABLE ROW LEVEL SECURITY;

--
-- Name: email_suppression; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE email_suppression ENABLE ROW LEVEL SECURITY;

--
-- Name: email_template; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE email_template ENABLE ROW LEVEL SECURITY;

--
-- Name: environment; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE environment ENABLE ROW LEVEL SECURITY;

--
-- Name: environment_promotion; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE environment_promotion ENABLE ROW LEVEL SECURITY;

--
-- Name: field_history; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE field_history ENABLE ROW LEVEL SECURITY;

--
-- Name: file_attachment; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE file_attachment ENABLE ROW LEVEL SECURITY;

--
-- Name: flow; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE flow ENABLE ROW LEVEL SECURITY;

--
-- Name: flow_execution; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE flow_execution ENABLE ROW LEVEL SECURITY;

--
-- Name: global_picklist; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE global_picklist ENABLE ROW LEVEL SECURITY;

--
-- Name: group_membership; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE group_membership ENABLE ROW LEVEL SECURITY;

--
-- Name: layout_field; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE layout_field ENABLE ROW LEVEL SECURITY;

--
-- Name: layout_related_list; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE layout_related_list ENABLE ROW LEVEL SECURITY;

--
-- Name: layout_rule; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE layout_rule ENABLE ROW LEVEL SECURITY;

--
-- Name: layout_section; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE layout_section ENABLE ROW LEVEL SECURITY;

--
-- Name: list_view; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE list_view ENABLE ROW LEVEL SECURITY;

--
-- Name: login_history; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE login_history ENABLE ROW LEVEL SECURITY;

--
-- Name: metadata_snapshot; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE metadata_snapshot ENABLE ROW LEVEL SECURITY;

--
-- Name: migration_run; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE migration_run ENABLE ROW LEVEL SECURITY;

--
-- Name: note; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE note ENABLE ROW LEVEL SECURITY;

--
-- Name: oidc_provider; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE oidc_provider ENABLE ROW LEVEL SECURITY;

--
-- Name: package; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE package ENABLE ROW LEVEL SECURITY;

--
-- Name: page_layout; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE page_layout ENABLE ROW LEVEL SECURITY;

--
-- Name: picklist_dependency; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE picklist_dependency ENABLE ROW LEVEL SECURITY;

--
-- Name: picklist_value; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE picklist_value ENABLE ROW LEVEL SECURITY;

--
-- Name: platform_user; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE platform_user ENABLE ROW LEVEL SECURITY;

--
-- Name: profile; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE profile ENABLE ROW LEVEL SECURITY;

--
-- Name: profile_field_permission; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE profile_field_permission ENABLE ROW LEVEL SECURITY;

--
-- Name: profile_object_permission; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE profile_object_permission ENABLE ROW LEVEL SECURITY;

--
-- Name: profile_system_permission; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE profile_system_permission ENABLE ROW LEVEL SECURITY;

--
-- Name: promotion_item; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE promotion_item ENABLE ROW LEVEL SECURITY;

--
-- Name: quick_action; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE quick_action ENABLE ROW LEVEL SECURITY;

--
-- Name: record_script; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE record_script ENABLE ROW LEVEL SECURITY;

--
-- Name: record_share; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE record_share ENABLE ROW LEVEL SECURITY;

--
-- Name: record_tombstone; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE record_tombstone ENABLE ROW LEVEL SECURITY;

--
-- Name: record_type; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE record_type ENABLE ROW LEVEL SECURITY;

--
-- Name: report; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE report ENABLE ROW LEVEL SECURITY;

--
-- Name: report_folder; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE report_folder ENABLE ROW LEVEL SECURITY;

--
-- Name: saml_provider; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE saml_provider ENABLE ROW LEVEL SECURITY;

--
-- Name: scheduled_job; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE scheduled_job ENABLE ROW LEVEL SECURITY;

--
-- Name: script; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE script ENABLE ROW LEVEL SECURITY;

--
-- Name: search_index; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE search_index ENABLE ROW LEVEL SECURITY;

--
-- Name: security_audit_log; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE security_audit_log ENABLE ROW LEVEL SECURITY;

--
-- Name: setup_audit_trail; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE setup_audit_trail ENABLE ROW LEVEL SECURITY;

--
-- Name: api_call_idempotency tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON api_call_idempotency USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: api_operation tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON api_operation USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: api_spec tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON api_spec USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: approval_instance tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON approval_instance USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: approval_process tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON approval_process USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: bulk_job tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON bulk_job USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: collection tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON collection USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: connected_app tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON connected_app USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: credential tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON credential USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: credential_oauth_token tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON credential_oauth_token USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: dashboard tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON dashboard USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: dashboard_component tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON dashboard_component USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: delegated_admin_scope tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON delegated_admin_scope USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: email_campaign tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON email_campaign USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: email_campaign_recipient tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON email_campaign_recipient USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: email_log tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON email_log USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: email_suppression tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON email_suppression USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: email_template tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON email_template USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: environment tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON environment USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: environment_promotion tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON environment_promotion USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: field_history tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON field_history USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: file_attachment tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON file_attachment USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: flow tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON flow USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: flow_execution tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON flow_execution USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: global_picklist tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON global_picklist USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: group_membership tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON group_membership USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: layout_field tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON layout_field USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: layout_related_list tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON layout_related_list USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: layout_rule tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON layout_rule USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: layout_section tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON layout_section USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: list_view tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON list_view USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: login_history tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON login_history USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: metadata_snapshot tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON metadata_snapshot USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: migration_run tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON migration_run USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: note tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON note USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: oidc_provider tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON oidc_provider USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: package tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON package USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: page_layout tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON page_layout USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: picklist_dependency tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON picklist_dependency USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: picklist_value tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON picklist_value USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: platform_user tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON platform_user USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: profile tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON profile USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: profile_field_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON profile_field_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: profile_object_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON profile_object_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: profile_system_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON profile_system_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: promotion_item tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON promotion_item USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: quick_action tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON quick_action USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: record_script tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON record_script USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: record_share tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON record_share USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: record_tombstone tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON record_tombstone USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: record_type tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON record_type USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: report tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON report USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: report_folder tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON report_folder USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: saml_provider tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON saml_provider USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: scheduled_job tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON scheduled_job USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: script tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON script USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: search_index tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON search_index USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: security_audit_log tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON security_audit_log USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: setup_audit_trail tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON setup_audit_trail USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: tenant_module_action tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON tenant_module_action USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: tenant_otlp_target tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON tenant_otlp_target USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: ui_menu tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON ui_menu USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: ui_menu_item tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON ui_menu_item USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: ui_page tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON ui_page USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: user_group tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON user_group USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: validation_rule tenant_isolation; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY tenant_isolation ON validation_rule USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));


--
-- Name: tenant_module_action; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE tenant_module_action ENABLE ROW LEVEL SECURITY;

--
-- Name: tenant_otlp_target; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE tenant_otlp_target ENABLE ROW LEVEL SECURITY;

--
-- Name: ui_menu; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE ui_menu ENABLE ROW LEVEL SECURITY;

--
-- Name: ui_menu_item; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE ui_menu_item ENABLE ROW LEVEL SECURITY;

--
-- Name: ui_page; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE ui_page ENABLE ROW LEVEL SECURITY;

--
-- Name: user_group; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE user_group ENABLE ROW LEVEL SECURITY;

--
-- Name: validation_rule; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE validation_rule ENABLE ROW LEVEL SECURITY;

--
-- PostgreSQL database dump complete
--



-- Out-of-band FK/audit indexes present on the live DB but never added by a migration.
-- Codified here (idempotent) so fresh installs match production.
CREATE INDEX IF NOT EXISTS idx_approval_process_collection_id ON approval_process USING btree (collection_id);
CREATE INDEX IF NOT EXISTS idx_bulk_job_collection_id ON bulk_job USING btree (collection_id);
CREATE INDEX IF NOT EXISTS idx_email_template_related_collection ON email_template USING btree (related_collection_id);
CREATE INDEX IF NOT EXISTS idx_file_attachment_collection_id ON file_attachment USING btree (collection_id);
CREATE INDEX IF NOT EXISTS idx_layout_related_list_related_coll ON layout_related_list USING btree (related_collection_id);
CREATE INDEX IF NOT EXISTS idx_list_view_collection_id ON list_view USING btree (collection_id);
CREATE INDEX IF NOT EXISTS idx_note_collection_id ON note USING btree (collection_id);
CREATE INDEX IF NOT EXISTS idx_page_layout_collection_id ON page_layout USING btree (collection_id);
CREATE INDEX IF NOT EXISTS idx_search_index_collection_id ON search_index USING btree (collection_id);
CREATE INDEX IF NOT EXISTS idx_setup_audit_trail_tenant_created ON setup_audit_trail USING btree (tenant_id, created_at DESC);
