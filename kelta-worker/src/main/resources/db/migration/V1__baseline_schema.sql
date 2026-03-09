-- ============================================================================
-- V1: Baseline schema
-- ============================================================================
-- This migration represents the consolidated baseline of all previous
-- migrations (V1-V92). It contains the complete DDL for the public schema.
--
-- Generated from pg_dump of the running database on 2026-03-09.
-- ============================================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";



-- Name: search_index_update_vector(); Type: FUNCTION; Schema: public; Owner: -

CREATE FUNCTION public.search_index_update_vector() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.search_content, ''));
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

-- Name: approval_instance; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.approval_instance (
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

ALTER TABLE ONLY public.approval_instance FORCE ROW LEVEL SECURITY;

-- Name: approval_process; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.approval_process (
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

ALTER TABLE ONLY public.approval_process FORCE ROW LEVEL SECURITY;

-- Name: approval_step; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.approval_step (
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

-- Name: approval_step_instance; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.approval_step_instance (
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

-- Name: bulk_job; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.bulk_job (
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
    CONSTRAINT chk_bulk_operation CHECK (((operation)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'UPSERT'::character varying, 'DELETE'::character varying])::text[]))),
    CONSTRAINT chk_bulk_status CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'ABORTED'::character varying])::text[])))
);

ALTER TABLE ONLY public.bulk_job FORCE ROW LEVEL SECURITY;

-- Name: bulk_job_result; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.bulk_job_result (
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

-- Name: collection; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.collection (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.collection FORCE ROW LEVEL SECURITY;

-- Name: collection_version; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.collection_version (
    id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    version integer NOT NULL,
    schema jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

-- Name: connected_app; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.connected_app (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.connected_app FORCE ROW LEVEL SECURITY;

-- Name: connected_app_token; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.connected_app_token (
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

-- Name: dashboard; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.dashboard (
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

ALTER TABLE ONLY public.dashboard FORCE ROW LEVEL SECURITY;

-- Name: dashboard_component; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.dashboard_component (
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

ALTER TABLE ONLY public.dashboard_component FORCE ROW LEVEL SECURITY;

-- Name: email_log; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.email_log (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.email_log FORCE ROW LEVEL SECURITY;

-- Name: email_template; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.email_template (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.email_template FORCE ROW LEVEL SECURITY;

-- Name: emf_migrations; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.emf_migrations (
    id integer NOT NULL,
    collection_name character varying(255) NOT NULL,
    migration_type character varying(50) NOT NULL,
    sql_statement text NOT NULL,
    executed_at timestamp without time zone NOT NULL
);

-- Name: emf_migrations_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.emf_migrations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: emf_migrations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.emf_migrations_id_seq OWNED BY public.emf_migrations.id;

-- Name: field; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.field (
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

-- Name: field_history; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.field_history (
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

ALTER TABLE ONLY public.field_history FORCE ROW LEVEL SECURITY;

-- Name: field_version; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.field_version (
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

-- Name: file_attachment; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.file_attachment (
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

ALTER TABLE ONLY public.file_attachment FORCE ROW LEVEL SECURITY;

-- Name: flow; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.flow (
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
    CONSTRAINT chk_flow_type CHECK (((flow_type)::text = ANY ((ARRAY['RECORD_TRIGGERED'::character varying, 'KAFKA_TRIGGERED'::character varying, 'SCHEDULED'::character varying, 'AUTOLAUNCHED'::character varying, 'SCREEN'::character varying])::text[])))
);

ALTER TABLE ONLY public.flow FORCE ROW LEVEL SECURITY;

-- Name: flow_audit_log; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.flow_audit_log (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    flow_id character varying(36) NOT NULL,
    action character varying(30) NOT NULL,
    user_id character varying(36),
    details jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

-- Name: flow_execution; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.flow_execution (
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

ALTER TABLE ONLY public.flow_execution FORCE ROW LEVEL SECURITY;

-- Name: flow_execution_dedup; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.flow_execution_dedup (
    event_id character varying(100) NOT NULL,
    flow_id character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

-- Name: flow_pending_resume; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.flow_pending_resume (
    id character varying(36) NOT NULL,
    execution_id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    resume_at timestamp with time zone,
    resume_event character varying(200),
    claimed_by character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

-- Name: flow_step_log; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.flow_step_log (
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

-- Name: flow_version; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.flow_version (
    id character varying(36) NOT NULL,
    flow_id character varying(36) NOT NULL,
    version_number integer NOT NULL,
    definition jsonb NOT NULL,
    change_summary character varying(500),
    created_by character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

-- Name: global_picklist; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.global_picklist (
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

ALTER TABLE ONLY public.global_picklist FORCE ROW LEVEL SECURITY;

-- Name: group_membership; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.group_membership (
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

ALTER TABLE ONLY public.group_membership FORCE ROW LEVEL SECURITY;

-- Name: group_permission_set; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.group_permission_set (
    id character varying(36) NOT NULL,
    group_id character varying(36) NOT NULL,
    permission_set_id character varying(36) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.group_permission_set FORCE ROW LEVEL SECURITY;

-- Name: job_execution_log; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.job_execution_log (
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

-- Name: kelta_migrations; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.kelta_migrations (
    id integer NOT NULL,
    collection_name character varying(255) NOT NULL,
    migration_type character varying(50) NOT NULL,
    sql_statement text NOT NULL,
    executed_at timestamp without time zone NOT NULL
);

-- Name: kelta_migrations_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.kelta_migrations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: kelta_migrations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.kelta_migrations_id_seq OWNED BY public.kelta_migrations.id;

-- Name: layout_assignment; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.layout_assignment (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    record_type_id character varying(36),
    layout_id character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

-- Name: layout_field; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.layout_field (
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

ALTER TABLE ONLY public.layout_field FORCE ROW LEVEL SECURITY;

-- Name: layout_related_list; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.layout_related_list (
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

ALTER TABLE ONLY public.layout_related_list FORCE ROW LEVEL SECURITY;

-- Name: layout_section; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.layout_section (
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

ALTER TABLE ONLY public.layout_section FORCE ROW LEVEL SECURITY;

-- Name: list_view; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.list_view (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.list_view FORCE ROW LEVEL SECURITY;

-- Name: login_history; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.login_history (
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

ALTER TABLE ONLY public.login_history FORCE ROW LEVEL SECURITY;

-- Name: migration_run; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.migration_run (
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
    CONSTRAINT chk_migration_run_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('ROLLED_BACK'::character varying)::text]))),
    CONSTRAINT chk_migration_run_versions CHECK (((from_version < to_version) OR (from_version > to_version)))
);

ALTER TABLE ONLY public.migration_run FORCE ROW LEVEL SECURITY;

-- Name: migration_step; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.migration_step (
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
    CONSTRAINT chk_migration_step_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('SKIPPED'::character varying)::text])))
);

-- Name: note; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.note (
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

ALTER TABLE ONLY public.note FORCE ROW LEVEL SECURITY;

-- Name: observability_settings; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.observability_settings (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    setting_key character varying(100) NOT NULL,
    setting_value character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

-- Name: oidc_provider; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.oidc_provider (
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
    CONSTRAINT chk_oidc_provider_issuer CHECK (((issuer)::text ~ '^https?://'::text)),
    CONSTRAINT chk_oidc_provider_roles_mapping_json CHECK (((roles_mapping IS NULL) OR ((roles_mapping)::jsonb IS NOT NULL)))
);

ALTER TABLE ONLY public.oidc_provider FORCE ROW LEVEL SECURITY;

-- Name: package; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.package (
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

ALTER TABLE ONLY public.package FORCE ROW LEVEL SECURITY;

-- Name: package_item; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.package_item (
    id character varying(36) NOT NULL,
    package_id character varying(36) NOT NULL,
    item_type character varying(50) NOT NULL,
    item_id character varying(36) NOT NULL,
    content jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_package_item_type CHECK (((item_type)::text = ANY (ARRAY[('COLLECTION'::character varying)::text, ('FIELD'::character varying)::text, ('ROLE'::character varying)::text, ('POLICY'::character varying)::text, ('ROUTE_POLICY'::character varying)::text, ('FIELD_POLICY'::character varying)::text, ('OIDC_PROVIDER'::character varying)::text, ('UI_PAGE'::character varying)::text, ('UI_MENU'::character varying)::text, ('UI_MENU_ITEM'::character varying)::text])))
);

-- Name: page_layout; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.page_layout (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.page_layout FORCE ROW LEVEL SECURITY;

-- Name: permission_set; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.permission_set (
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

ALTER TABLE ONLY public.permission_set FORCE ROW LEVEL SECURITY;

-- Name: permset_field_permission; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.permset_field_permission (
    id character varying(36) NOT NULL,
    permission_set_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    field_id character varying(36) NOT NULL,
    visibility character varying(20) DEFAULT 'VISIBLE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.permset_field_permission FORCE ROW LEVEL SECURITY;

-- Name: permset_object_permission; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.permset_object_permission (
    id character varying(36) NOT NULL,
    permission_set_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    can_create boolean DEFAULT false NOT NULL,
    can_read boolean DEFAULT false NOT NULL,
    can_edit boolean DEFAULT false NOT NULL,
    can_delete boolean DEFAULT false NOT NULL,
    can_view_all boolean DEFAULT false NOT NULL,
    can_modify_all boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.permset_object_permission FORCE ROW LEVEL SECURITY;

-- Name: permset_system_permission; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.permset_system_permission (
    id character varying(36) NOT NULL,
    permission_set_id character varying(36) NOT NULL,
    permission_name character varying(100) NOT NULL,
    granted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.permset_system_permission FORCE ROW LEVEL SECURITY;

-- Name: picklist_dependency; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.picklist_dependency (
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

ALTER TABLE ONLY public.picklist_dependency FORCE ROW LEVEL SECURITY;

-- Name: picklist_value; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.picklist_value (
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

ALTER TABLE ONLY public.picklist_value FORCE ROW LEVEL SECURITY;

-- Name: platform_user; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.platform_user (
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
    CONSTRAINT chk_user_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'LOCKED'::character varying, 'PENDING_ACTIVATION'::character varying])::text[])))
);

ALTER TABLE ONLY public.platform_user FORCE ROW LEVEL SECURITY;

-- Name: profile; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.profile (
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

ALTER TABLE ONLY public.profile FORCE ROW LEVEL SECURITY;

-- Name: profile_field_permission; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.profile_field_permission (
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

ALTER TABLE ONLY public.profile_field_permission FORCE ROW LEVEL SECURITY;

-- Name: profile_object_permission; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.profile_object_permission (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    collection_id character varying(36) NOT NULL,
    can_create boolean DEFAULT false NOT NULL,
    can_read boolean DEFAULT false NOT NULL,
    can_edit boolean DEFAULT false NOT NULL,
    can_delete boolean DEFAULT false NOT NULL,
    can_view_all boolean DEFAULT false NOT NULL,
    can_modify_all boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    tenant_id character varying(36) NOT NULL
);

ALTER TABLE ONLY public.profile_object_permission FORCE ROW LEVEL SECURITY;

-- Name: profile_system_permission; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.profile_system_permission (
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

ALTER TABLE ONLY public.profile_system_permission FORCE ROW LEVEL SECURITY;

-- Name: record_type; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.record_type (
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

ALTER TABLE ONLY public.record_type FORCE ROW LEVEL SECURITY;

-- Name: record_type_picklist; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.record_type_picklist (
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

-- Name: report; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.report (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.report FORCE ROW LEVEL SECURITY;

-- Name: report_folder; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.report_folder (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    access_level character varying(20) DEFAULT 'PRIVATE'::character varying,
    created_by character varying(36),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255)
);

ALTER TABLE ONLY public.report_folder FORCE ROW LEVEL SECURITY;

-- Name: scheduled_job; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.scheduled_job (
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
    CONSTRAINT chk_job_type CHECK (((job_type)::text = ANY ((ARRAY['FLOW'::character varying, 'SCRIPT'::character varying, 'REPORT_EXPORT'::character varying])::text[])))
);

ALTER TABLE ONLY public.scheduled_job FORCE ROW LEVEL SECURITY;

-- Name: script; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.script (
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
    CONSTRAINT chk_script_type CHECK (((script_type)::text = ANY ((ARRAY['BEFORE_TRIGGER'::character varying, 'AFTER_TRIGGER'::character varying, 'SCHEDULED'::character varying, 'API_ENDPOINT'::character varying, 'VALIDATION'::character varying, 'EVENT_HANDLER'::character varying, 'EMAIL_HANDLER'::character varying])::text[])))
);

ALTER TABLE ONLY public.script FORCE ROW LEVEL SECURITY;

-- Name: script_execution_log; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.script_execution_log (
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

-- Name: script_trigger; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.script_trigger (
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

-- Name: search_index; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.search_index (
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

ALTER TABLE ONLY public.search_index FORCE ROW LEVEL SECURITY;

-- Name: security_audit_log; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.security_audit_log (
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

ALTER TABLE ONLY public.security_audit_log FORCE ROW LEVEL SECURITY;

-- Name: setup_audit_trail; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.setup_audit_trail (
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

ALTER TABLE ONLY public.setup_audit_trail FORCE ROW LEVEL SECURITY;

-- Name: tenant; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.tenant (
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
    CONSTRAINT chk_tenant_edition CHECK (((edition)::text = ANY ((ARRAY['FREE'::character varying, 'PROFESSIONAL'::character varying, 'ENTERPRISE'::character varying, 'UNLIMITED'::character varying])::text[]))),
    CONSTRAINT chk_tenant_slug CHECK (((slug)::text ~ '^[a-z][a-z0-9-]{1,61}[a-z0-9]$'::text)),
    CONSTRAINT chk_tenant_status CHECK (((status)::text = ANY ((ARRAY['PROVISIONING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DECOMMISSIONED'::character varying])::text[])))
);

-- Name: tenant_module; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.tenant_module (
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
    CONSTRAINT chk_module_status CHECK (((status)::text = ANY ((ARRAY['INSTALLING'::character varying, 'INSTALLED'::character varying, 'ACTIVE'::character varying, 'DISABLED'::character varying, 'FAILED'::character varying, 'UNINSTALLING'::character varying])::text[])))
);

-- Name: tenant_module_action; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.tenant_module_action (
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

ALTER TABLE ONLY public.tenant_module_action FORCE ROW LEVEL SECURITY;

-- Name: ui_menu; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.ui_menu (
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

ALTER TABLE ONLY public.ui_menu FORCE ROW LEVEL SECURITY;

-- Name: ui_menu_item; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.ui_menu_item (
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

ALTER TABLE ONLY public.ui_menu_item FORCE ROW LEVEL SECURITY;

-- Name: ui_page; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.ui_page (
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
    updated_by character varying(255)
);

ALTER TABLE ONLY public.ui_page FORCE ROW LEVEL SECURITY;

-- Name: user_group; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.user_group (
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

ALTER TABLE ONLY public.user_group FORCE ROW LEVEL SECURITY;

-- Name: user_group_member; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.user_group_member (
    group_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL
);

-- Name: user_permission_set; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.user_permission_set (
    id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    permission_set_id character varying(36) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255)
);

-- Name: validation_rule; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.validation_rule (
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
    CONSTRAINT chk_evaluate_on CHECK (((evaluate_on)::text = ANY ((ARRAY['CREATE'::character varying, 'UPDATE'::character varying, 'CREATE_AND_UPDATE'::character varying])::text[])))
);

ALTER TABLE ONLY public.validation_rule FORCE ROW LEVEL SECURITY;

-- Name: webhook; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.webhook (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(200) NOT NULL,
    url character varying(2048) NOT NULL,
    events jsonb NOT NULL,
    collection_id character varying(36),
    filter_formula text,
    headers jsonb DEFAULT '{}'::jsonb,
    secret character varying(200),
    active boolean DEFAULT true,
    retry_policy jsonb DEFAULT '{"maxRetries": 3, "backoffSeconds": [10, 60, 300]}'::jsonb,
    created_by character varying(36) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255)
);

ALTER TABLE ONLY public.webhook FORCE ROW LEVEL SECURITY;

-- Name: webhook_delivery; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.webhook_delivery (
    id character varying(36) NOT NULL,
    webhook_id character varying(36) NOT NULL,
    event_type character varying(50) NOT NULL,
    payload jsonb NOT NULL,
    response_status integer,
    response_body text,
    attempt_count integer DEFAULT 1,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    next_retry_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    delivered_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chk_delivery_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying, 'RETRYING'::character varying])::text[])))
);

-- Name: emf_migrations id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.emf_migrations ALTER COLUMN id SET DEFAULT nextval('public.emf_migrations_id_seq'::regclass);

-- Name: kelta_migrations id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.kelta_migrations ALTER COLUMN id SET DEFAULT nextval('public.kelta_migrations_id_seq'::regclass);

-- Name: approval_instance approval_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_instance
    ADD CONSTRAINT approval_instance_pkey PRIMARY KEY (id);

-- Name: approval_process approval_process_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_process
    ADD CONSTRAINT approval_process_pkey PRIMARY KEY (id);

-- Name: approval_step_instance approval_step_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_step_instance
    ADD CONSTRAINT approval_step_instance_pkey PRIMARY KEY (id);

-- Name: approval_step approval_step_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_step
    ADD CONSTRAINT approval_step_pkey PRIMARY KEY (id);

-- Name: bulk_job bulk_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.bulk_job
    ADD CONSTRAINT bulk_job_pkey PRIMARY KEY (id);

-- Name: bulk_job_result bulk_job_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.bulk_job_result
    ADD CONSTRAINT bulk_job_result_pkey PRIMARY KEY (id);

-- Name: collection collection_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection
    ADD CONSTRAINT collection_pkey PRIMARY KEY (id);

-- Name: collection_version collection_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection_version
    ADD CONSTRAINT collection_version_pkey PRIMARY KEY (id);

-- Name: connected_app connected_app_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app
    ADD CONSTRAINT connected_app_client_id_key UNIQUE (client_id);

-- Name: connected_app connected_app_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app
    ADD CONSTRAINT connected_app_pkey PRIMARY KEY (id);

-- Name: connected_app_token connected_app_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app_token
    ADD CONSTRAINT connected_app_token_pkey PRIMARY KEY (id);

-- Name: dashboard_component dashboard_component_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard_component
    ADD CONSTRAINT dashboard_component_pkey PRIMARY KEY (id);

-- Name: dashboard dashboard_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard
    ADD CONSTRAINT dashboard_pkey PRIMARY KEY (id);

-- Name: email_log email_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_log
    ADD CONSTRAINT email_log_pkey PRIMARY KEY (id);

-- Name: email_template email_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_template
    ADD CONSTRAINT email_template_pkey PRIMARY KEY (id);

-- Name: emf_migrations emf_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.emf_migrations
    ADD CONSTRAINT emf_migrations_pkey PRIMARY KEY (id);

-- Name: field_history field_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field_history
    ADD CONSTRAINT field_history_pkey PRIMARY KEY (id);

-- Name: field field_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field
    ADD CONSTRAINT field_pkey PRIMARY KEY (id);

-- Name: field_version field_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field_version
    ADD CONSTRAINT field_version_pkey PRIMARY KEY (id);

-- Name: file_attachment file_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.file_attachment
    ADD CONSTRAINT file_attachment_pkey PRIMARY KEY (id);

-- Name: flow_audit_log flow_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_audit_log
    ADD CONSTRAINT flow_audit_log_pkey PRIMARY KEY (id);

-- Name: flow_execution_dedup flow_execution_dedup_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_execution_dedup
    ADD CONSTRAINT flow_execution_dedup_pkey PRIMARY KEY (event_id, flow_id);

-- Name: flow_execution flow_execution_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_execution
    ADD CONSTRAINT flow_execution_pkey PRIMARY KEY (id);

-- Name: flow_pending_resume flow_pending_resume_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_pending_resume
    ADD CONSTRAINT flow_pending_resume_pkey PRIMARY KEY (id);

-- Name: flow flow_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow
    ADD CONSTRAINT flow_pkey PRIMARY KEY (id);

-- Name: flow_step_log flow_step_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_step_log
    ADD CONSTRAINT flow_step_log_pkey PRIMARY KEY (id);

-- Name: flow_version flow_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_version
    ADD CONSTRAINT flow_version_pkey PRIMARY KEY (id);

-- Name: global_picklist global_picklist_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.global_picklist
    ADD CONSTRAINT global_picklist_pkey PRIMARY KEY (id);

-- Name: group_membership group_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT group_membership_pkey PRIMARY KEY (id);

-- Name: group_permission_set group_permission_set_group_id_permission_set_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_permission_set
    ADD CONSTRAINT group_permission_set_group_id_permission_set_id_key UNIQUE (group_id, permission_set_id);

-- Name: group_permission_set group_permission_set_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_permission_set
    ADD CONSTRAINT group_permission_set_pkey PRIMARY KEY (id);

-- Name: job_execution_log job_execution_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.job_execution_log
    ADD CONSTRAINT job_execution_log_pkey PRIMARY KEY (id);

-- Name: kelta_migrations kelta_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.kelta_migrations
    ADD CONSTRAINT kelta_migrations_pkey PRIMARY KEY (id);

-- Name: layout_assignment layout_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_assignment
    ADD CONSTRAINT layout_assignment_pkey PRIMARY KEY (id);

-- Name: layout_field layout_field_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_field
    ADD CONSTRAINT layout_field_pkey PRIMARY KEY (id);

-- Name: layout_related_list layout_related_list_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_related_list
    ADD CONSTRAINT layout_related_list_pkey PRIMARY KEY (id);

-- Name: layout_section layout_section_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_section
    ADD CONSTRAINT layout_section_pkey PRIMARY KEY (id);

-- Name: list_view list_view_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.list_view
    ADD CONSTRAINT list_view_pkey PRIMARY KEY (id);

-- Name: login_history login_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.login_history
    ADD CONSTRAINT login_history_pkey PRIMARY KEY (id);

-- Name: migration_run migration_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.migration_run
    ADD CONSTRAINT migration_run_pkey PRIMARY KEY (id);

-- Name: migration_step migration_step_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.migration_step
    ADD CONSTRAINT migration_step_pkey PRIMARY KEY (id);

-- Name: note note_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.note
    ADD CONSTRAINT note_pkey PRIMARY KEY (id);

-- Name: observability_settings observability_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.observability_settings
    ADD CONSTRAINT observability_settings_pkey PRIMARY KEY (id);

-- Name: oidc_provider oidc_provider_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.oidc_provider
    ADD CONSTRAINT oidc_provider_pkey PRIMARY KEY (id);

-- Name: package_item package_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.package_item
    ADD CONSTRAINT package_item_pkey PRIMARY KEY (id);

-- Name: package package_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.package
    ADD CONSTRAINT package_pkey PRIMARY KEY (id);

-- Name: page_layout page_layout_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.page_layout
    ADD CONSTRAINT page_layout_pkey PRIMARY KEY (id);

-- Name: permission_set permission_set_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permission_set
    ADD CONSTRAINT permission_set_pkey PRIMARY KEY (id);

-- Name: permission_set permission_set_tenant_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permission_set
    ADD CONSTRAINT permission_set_tenant_id_name_key UNIQUE (tenant_id, name);

-- Name: permset_field_permission permset_field_permission_permission_set_id_field_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_field_permission
    ADD CONSTRAINT permset_field_permission_permission_set_id_field_id_key UNIQUE (permission_set_id, field_id);

-- Name: permset_field_permission permset_field_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_field_permission
    ADD CONSTRAINT permset_field_permission_pkey PRIMARY KEY (id);

-- Name: permset_object_permission permset_object_permission_permission_set_id_collection_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_object_permission
    ADD CONSTRAINT permset_object_permission_permission_set_id_collection_id_key UNIQUE (permission_set_id, collection_id);

-- Name: permset_object_permission permset_object_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_object_permission
    ADD CONSTRAINT permset_object_permission_pkey PRIMARY KEY (id);

-- Name: permset_system_permission permset_system_permission_permission_set_id_permission_name_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_system_permission
    ADD CONSTRAINT permset_system_permission_permission_set_id_permission_name_key UNIQUE (permission_set_id, permission_name);

-- Name: permset_system_permission permset_system_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_system_permission
    ADD CONSTRAINT permset_system_permission_pkey PRIMARY KEY (id);

-- Name: picklist_dependency picklist_dependency_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_dependency
    ADD CONSTRAINT picklist_dependency_pkey PRIMARY KEY (id);

-- Name: picklist_value picklist_value_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_value
    ADD CONSTRAINT picklist_value_pkey PRIMARY KEY (id);

-- Name: platform_user platform_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.platform_user
    ADD CONSTRAINT platform_user_pkey PRIMARY KEY (id);

-- Name: profile_field_permission profile_field_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_field_permission
    ADD CONSTRAINT profile_field_permission_pkey PRIMARY KEY (id);

-- Name: profile_field_permission profile_field_permission_profile_id_field_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_field_permission
    ADD CONSTRAINT profile_field_permission_profile_id_field_id_key UNIQUE (profile_id, field_id);

-- Name: profile_object_permission profile_object_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_object_permission
    ADD CONSTRAINT profile_object_permission_pkey PRIMARY KEY (id);

-- Name: profile_object_permission profile_object_permission_profile_id_collection_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_object_permission
    ADD CONSTRAINT profile_object_permission_profile_id_collection_id_key UNIQUE (profile_id, collection_id);

-- Name: profile profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile
    ADD CONSTRAINT profile_pkey PRIMARY KEY (id);

-- Name: profile_system_permission profile_system_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_system_permission
    ADD CONSTRAINT profile_system_permission_pkey PRIMARY KEY (id);

-- Name: profile_system_permission profile_system_permission_profile_id_permission_name_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_system_permission
    ADD CONSTRAINT profile_system_permission_profile_id_permission_name_key UNIQUE (profile_id, permission_name);

-- Name: profile profile_tenant_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile
    ADD CONSTRAINT profile_tenant_id_name_key UNIQUE (tenant_id, name);

-- Name: record_type_picklist record_type_picklist_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type_picklist
    ADD CONSTRAINT record_type_picklist_pkey PRIMARY KEY (id);

-- Name: record_type record_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT record_type_pkey PRIMARY KEY (id);

-- Name: report_folder report_folder_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report_folder
    ADD CONSTRAINT report_folder_pkey PRIMARY KEY (id);

-- Name: report report_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_pkey PRIMARY KEY (id);

-- Name: scheduled_job scheduled_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.scheduled_job
    ADD CONSTRAINT scheduled_job_pkey PRIMARY KEY (id);

-- Name: script_execution_log script_execution_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script_execution_log
    ADD CONSTRAINT script_execution_log_pkey PRIMARY KEY (id);

-- Name: script script_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script
    ADD CONSTRAINT script_pkey PRIMARY KEY (id);

-- Name: script_trigger script_trigger_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script_trigger
    ADD CONSTRAINT script_trigger_pkey PRIMARY KEY (id);

-- Name: search_index search_index_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.search_index
    ADD CONSTRAINT search_index_pkey PRIMARY KEY (id);

-- Name: security_audit_log security_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.security_audit_log
    ADD CONSTRAINT security_audit_log_pkey PRIMARY KEY (id);

-- Name: setup_audit_trail setup_audit_trail_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.setup_audit_trail
    ADD CONSTRAINT setup_audit_trail_pkey PRIMARY KEY (id);

-- Name: tenant_module_action tenant_module_action_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant_module_action
    ADD CONSTRAINT tenant_module_action_pkey PRIMARY KEY (id);

-- Name: tenant_module tenant_module_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant_module
    ADD CONSTRAINT tenant_module_pkey PRIMARY KEY (id);

-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);

-- Name: tenant tenant_slug_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_slug_key UNIQUE (slug);

-- Name: ui_menu_item ui_menu_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_menu_item
    ADD CONSTRAINT ui_menu_item_pkey PRIMARY KEY (id);

-- Name: ui_menu ui_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_menu
    ADD CONSTRAINT ui_menu_pkey PRIMARY KEY (id);

-- Name: ui_page ui_page_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_page
    ADD CONSTRAINT ui_page_pkey PRIMARY KEY (id);

-- Name: collection_version uk_collection_version; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection_version
    ADD CONSTRAINT uk_collection_version UNIQUE (collection_id, version);

-- Name: field uk_field_collection_name; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field
    ADD CONSTRAINT uk_field_collection_name UNIQUE (collection_id, name);

-- Name: migration_step uk_migration_step_run_number; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.migration_step
    ADD CONSTRAINT uk_migration_step_run_number UNIQUE (migration_run_id, step_number);

-- Name: search_index uk_search_index_tenant_collection_record; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.search_index
    ADD CONSTRAINT uk_search_index_tenant_collection_record UNIQUE (tenant_id, collection_name, record_id);

-- Name: approval_process uq_approval_process; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_process
    ADD CONSTRAINT uq_approval_process UNIQUE (tenant_id, collection_id, name);

-- Name: approval_step uq_approval_step; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_step
    ADD CONSTRAINT uq_approval_step UNIQUE (approval_process_id, step_number);

-- Name: collection uq_collection_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection
    ADD CONSTRAINT uq_collection_tenant_name UNIQUE (tenant_id, name);

-- Name: connected_app uq_connected_app; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app
    ADD CONSTRAINT uq_connected_app UNIQUE (tenant_id, name);

-- Name: email_template uq_email_template; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_template
    ADD CONSTRAINT uq_email_template UNIQUE (tenant_id, name);

-- Name: flow uq_flow; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow
    ADD CONSTRAINT uq_flow UNIQUE (tenant_id, name);

-- Name: flow_version uq_flow_version; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_version
    ADD CONSTRAINT uq_flow_version UNIQUE (flow_id, version_number);

-- Name: global_picklist uq_global_picklist; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.global_picklist
    ADD CONSTRAINT uq_global_picklist UNIQUE (tenant_id, name);

-- Name: group_membership uq_group_membership; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT uq_group_membership UNIQUE (group_id, member_type, member_id);

-- Name: user_group uq_group_name; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group
    ADD CONSTRAINT uq_group_name UNIQUE (tenant_id, name);

-- Name: page_layout uq_layout; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.page_layout
    ADD CONSTRAINT uq_layout UNIQUE (tenant_id, collection_id, name);

-- Name: layout_assignment uq_layout_assign; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_assignment
    ADD CONSTRAINT uq_layout_assign UNIQUE (tenant_id, collection_id, profile_id, record_type_id);

-- Name: list_view uq_list_view; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.list_view
    ADD CONSTRAINT uq_list_view UNIQUE (tenant_id, collection_id, name, created_by);

-- Name: observability_settings uq_observability_settings_tenant_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.observability_settings
    ADD CONSTRAINT uq_observability_settings_tenant_key UNIQUE (tenant_id, setting_key);

-- Name: oidc_provider uq_oidc_provider_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.oidc_provider
    ADD CONSTRAINT uq_oidc_provider_tenant_name UNIQUE (tenant_id, name);

-- Name: package uq_package_tenant_name_version; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.package
    ADD CONSTRAINT uq_package_tenant_name_version UNIQUE (tenant_id, name, version);

-- Name: picklist_dependency uq_picklist_dep; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_dependency
    ADD CONSTRAINT uq_picklist_dep UNIQUE (controlling_field_id, dependent_field_id);

-- Name: picklist_value uq_picklist_value; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_value
    ADD CONSTRAINT uq_picklist_value UNIQUE (picklist_source_type, picklist_source_id, value);

-- Name: record_type uq_record_type_name; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT uq_record_type_name UNIQUE (tenant_id, collection_id, name);

-- Name: record_type_picklist uq_record_type_picklist; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type_picklist
    ADD CONSTRAINT uq_record_type_picklist UNIQUE (record_type_id, field_id);

-- Name: report_folder uq_report_folder; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report_folder
    ADD CONSTRAINT uq_report_folder UNIQUE (tenant_id, name, created_by);

-- Name: scheduled_job uq_scheduled_job; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.scheduled_job
    ADD CONSTRAINT uq_scheduled_job UNIQUE (tenant_id, name);

-- Name: script uq_script; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script
    ADD CONSTRAINT uq_script UNIQUE (tenant_id, name);

-- Name: script_trigger uq_script_trigger; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script_trigger
    ADD CONSTRAINT uq_script_trigger UNIQUE (script_id, collection_id, trigger_event);

-- Name: tenant_module uq_tenant_module; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant_module
    ADD CONSTRAINT uq_tenant_module UNIQUE (tenant_id, module_id);

-- Name: connected_app_token uq_token_hash; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app_token
    ADD CONSTRAINT uq_token_hash UNIQUE (token_hash);

-- Name: ui_menu uq_ui_menu_tenant_name; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_menu
    ADD CONSTRAINT uq_ui_menu_tenant_name UNIQUE (tenant_id, name);

-- Name: ui_page uq_ui_page_tenant_path; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_page
    ADD CONSTRAINT uq_ui_page_tenant_path UNIQUE (tenant_id, path);

-- Name: platform_user uq_user_tenant_email; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.platform_user
    ADD CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email);

-- Name: validation_rule uq_validation_rule_name; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.validation_rule
    ADD CONSTRAINT uq_validation_rule_name UNIQUE (tenant_id, collection_id, name);

-- Name: webhook uq_webhook; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook
    ADD CONSTRAINT uq_webhook UNIQUE (tenant_id, name);

-- Name: user_group_member user_group_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group_member
    ADD CONSTRAINT user_group_member_pkey PRIMARY KEY (group_id, user_id);

-- Name: user_group user_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group
    ADD CONSTRAINT user_group_pkey PRIMARY KEY (id);

-- Name: user_permission_set user_permission_set_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_permission_set
    ADD CONSTRAINT user_permission_set_pkey PRIMARY KEY (id);

-- Name: user_permission_set user_permission_set_user_id_permission_set_id_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_permission_set
    ADD CONSTRAINT user_permission_set_user_id_permission_set_id_key UNIQUE (user_id, permission_set_id);

-- Name: validation_rule validation_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.validation_rule
    ADD CONSTRAINT validation_rule_pkey PRIMARY KEY (id);

-- Name: webhook_delivery webhook_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook_delivery
    ADD CONSTRAINT webhook_delivery_pkey PRIMARY KEY (id);

-- Name: webhook webhook_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook
    ADD CONSTRAINT webhook_pkey PRIMARY KEY (id);

-- Name: idx_approval_instance_record; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_approval_instance_record ON public.approval_instance USING btree (collection_id, record_id);

-- Name: idx_approval_process_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_approval_process_tenant ON public.approval_process USING btree (tenant_id, collection_id);

-- Name: idx_approval_step_instance_assigned; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_approval_step_instance_assigned ON public.approval_step_instance USING btree (assigned_to, status);

-- Name: idx_attachment_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_attachment_tenant ON public.file_attachment USING btree (tenant_id);

-- Name: idx_attachment_tenant_record; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_attachment_tenant_record ON public.file_attachment USING btree (tenant_id, collection_id, record_id, uploaded_at DESC);

-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_audit_entity ON public.setup_audit_trail USING btree (entity_type, entity_id);

-- Name: idx_audit_tenant_time; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_audit_tenant_time ON public.setup_audit_trail USING btree (tenant_id, "timestamp" DESC);

-- Name: idx_audit_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_audit_user ON public.setup_audit_trail USING btree (user_id, "timestamp" DESC);

-- Name: idx_bulk_job_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_bulk_job_status ON public.bulk_job USING btree (status);

-- Name: idx_bulk_job_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_bulk_job_tenant ON public.bulk_job USING btree (tenant_id, created_at DESC);

-- Name: idx_bulk_result_job; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_bulk_result_job ON public.bulk_job_result USING btree (bulk_job_id, record_index);

-- Name: idx_collection_active; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_active ON public.collection USING btree (active);

-- Name: idx_collection_created_at; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_created_at ON public.collection USING btree (created_at);

-- Name: idx_collection_display_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_display_name ON public.collection USING btree (display_name);

-- Name: idx_collection_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_name ON public.collection USING btree (name);

-- Name: idx_collection_system; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_system ON public.collection USING btree (system_collection);

-- Name: idx_collection_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_tenant ON public.collection USING btree (tenant_id);

-- Name: idx_collection_version_collection_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_version_collection_id ON public.collection_version USING btree (collection_id);

-- Name: idx_collection_version_schema_gin; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_version_schema_gin ON public.collection_version USING gin (schema jsonb_path_ops);

-- Name: idx_collection_version_version; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_collection_version_version ON public.collection_version USING btree (version);

-- Name: idx_connected_app_client; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_connected_app_client ON public.connected_app USING btree (client_id);

-- Name: idx_connected_app_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_connected_app_tenant ON public.connected_app USING btree (tenant_id);

-- Name: idx_dashboard_component; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_dashboard_component ON public.dashboard_component USING btree (dashboard_id, sort_order);

-- Name: idx_dashboard_component_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_dashboard_component_tenant_id ON public.dashboard_component USING btree (tenant_id);

-- Name: idx_dashboard_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_dashboard_tenant ON public.dashboard USING btree (tenant_id);

-- Name: idx_delivery_retry; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_delivery_retry ON public.webhook_delivery USING btree (status, next_retry_at);

-- Name: idx_delivery_webhook; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_delivery_webhook ON public.webhook_delivery USING btree (webhook_id, created_at DESC);

-- Name: idx_email_log_template; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_email_log_template ON public.email_log USING btree (template_id);

-- Name: idx_email_log_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_email_log_tenant ON public.email_log USING btree (tenant_id, status);

-- Name: idx_email_template_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_email_template_tenant ON public.email_template USING btree (tenant_id);

-- Name: idx_field_active; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_active ON public.field USING btree (active);

-- Name: idx_field_collection_active; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_collection_active ON public.field USING btree (collection_id, active);

-- Name: idx_field_collection_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_collection_id ON public.field USING btree (collection_id);

-- Name: idx_field_constraints_gin; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_constraints_gin ON public.field USING gin (constraints jsonb_path_ops);

-- Name: idx_field_display_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_display_name ON public.field USING btree (display_name);

-- Name: idx_field_history_field; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_history_field ON public.field_history USING btree (collection_id, field_name, changed_at DESC);

-- Name: idx_field_history_record; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_history_record ON public.field_history USING btree (collection_id, record_id, changed_at DESC);

-- Name: idx_field_history_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_history_tenant ON public.field_history USING btree (tenant_id);

-- Name: idx_field_history_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_history_user ON public.field_history USING btree (changed_by, changed_at DESC);

-- Name: idx_field_indexed; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_indexed ON public.field USING btree (indexed) WHERE (indexed = true);

-- Name: idx_field_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_name ON public.field USING btree (name);

-- Name: idx_field_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_order ON public.field USING btree (field_order);

-- Name: idx_field_reference_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_reference_collection ON public.field USING btree (reference_collection_id) WHERE (reference_collection_id IS NOT NULL);

-- Name: idx_field_reference_target; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_reference_target ON public.field USING btree (reference_target) WHERE (reference_target IS NOT NULL);

-- Name: idx_field_relationship_type; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_relationship_type ON public.field USING btree (relationship_type) WHERE (relationship_type IS NOT NULL);

-- Name: idx_field_type; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_type ON public.field USING btree (type);

-- Name: idx_field_unique; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_unique ON public.field USING btree (unique_constraint) WHERE (unique_constraint = true);

-- Name: idx_field_version_collection_version_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_version_collection_version_id ON public.field_version USING btree (collection_version_id);

-- Name: idx_field_version_field_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_field_version_field_id ON public.field_version USING btree (field_id);

-- Name: idx_flow_audit_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_audit_tenant ON public.flow_audit_log USING btree (tenant_id, flow_id);

-- Name: idx_flow_exec_flow; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_exec_flow ON public.flow_execution USING btree (flow_id);

-- Name: idx_flow_exec_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_exec_status ON public.flow_execution USING btree (tenant_id, status);

-- Name: idx_flow_execution_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_execution_status ON public.flow_execution USING btree (flow_id, status);

-- Name: idx_flow_pending_resume_event; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_pending_resume_event ON public.flow_pending_resume USING btree (resume_event) WHERE (claimed_by IS NULL);

-- Name: idx_flow_pending_resume_time; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_pending_resume_time ON public.flow_pending_resume USING btree (resume_at) WHERE (claimed_by IS NULL);

-- Name: idx_flow_step_log_exec; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_step_log_exec ON public.flow_step_log USING btree (execution_id);

-- Name: idx_flow_step_log_state; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_step_log_state ON public.flow_step_log USING btree (execution_id, state_id);

-- Name: idx_flow_step_log_time; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_step_log_time ON public.flow_step_log USING btree (execution_id, started_at);

-- Name: idx_flow_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_tenant ON public.flow USING btree (tenant_id);

-- Name: idx_flow_version_flow; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_flow_version_flow ON public.flow_version USING btree (flow_id, version_number DESC);

-- Name: idx_gm_group; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_gm_group ON public.group_membership USING btree (group_id);

-- Name: idx_gm_member; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_gm_member ON public.group_membership USING btree (member_type, member_id);

-- Name: idx_group_member_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_group_member_user ON public.user_group_member USING btree (user_id);

-- Name: idx_group_membership_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_group_membership_tenant_id ON public.group_membership USING btree (tenant_id);

-- Name: idx_group_permission_set_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_group_permission_set_tenant_id ON public.group_permission_set USING btree (tenant_id);

-- Name: idx_group_permset_group; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_group_permset_group ON public.group_permission_set USING btree (group_id);

-- Name: idx_group_permset_permset; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_group_permset_permset ON public.group_permission_set USING btree (permission_set_id);

-- Name: idx_job_exec_log; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_job_exec_log ON public.job_execution_log USING btree (job_id);

-- Name: idx_layout_field; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_layout_field ON public.layout_field USING btree (section_id, sort_order);

-- Name: idx_layout_field_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_layout_field_tenant_id ON public.layout_field USING btree (tenant_id);

-- Name: idx_layout_related_list_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_layout_related_list_tenant_id ON public.layout_related_list USING btree (tenant_id);

-- Name: idx_layout_section_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_layout_section_tenant_id ON public.layout_section USING btree (tenant_id);

-- Name: idx_list_view_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_list_view_collection ON public.list_view USING btree (tenant_id, collection_id);

-- Name: idx_list_view_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_list_view_user ON public.list_view USING btree (created_by);

-- Name: idx_login_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_login_tenant ON public.login_history USING btree (tenant_id, login_time DESC);

-- Name: idx_login_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_login_user ON public.login_history USING btree (user_id, login_time DESC);

-- Name: idx_migration_run_collection_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_run_collection_id ON public.migration_run USING btree (collection_id);

-- Name: idx_migration_run_created_at; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_run_created_at ON public.migration_run USING btree (created_at);

-- Name: idx_migration_run_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_run_status ON public.migration_run USING btree (status);

-- Name: idx_migration_run_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_run_tenant ON public.migration_run USING btree (tenant_id);

-- Name: idx_migration_step_details_gin; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_step_details_gin ON public.migration_step USING gin (details jsonb_path_ops);

-- Name: idx_migration_step_migration_run_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_step_migration_run_id ON public.migration_step USING btree (migration_run_id);

-- Name: idx_migration_step_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_step_status ON public.migration_step USING btree (status);

-- Name: idx_migration_step_step_number; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_migration_step_step_number ON public.migration_step USING btree (step_number);

-- Name: idx_note_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_note_tenant ON public.note USING btree (tenant_id);

-- Name: idx_note_tenant_record; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_note_tenant_record ON public.note USING btree (tenant_id, collection_id, record_id, created_at DESC);

-- Name: idx_oidc_provider_active; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_oidc_provider_active ON public.oidc_provider USING btree (active);

-- Name: idx_oidc_provider_issuer; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_oidc_provider_issuer ON public.oidc_provider USING btree (issuer);

-- Name: idx_oidc_provider_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_oidc_provider_name ON public.oidc_provider USING btree (name);

-- Name: idx_oidc_provider_roles_claim; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_oidc_provider_roles_claim ON public.oidc_provider USING btree (roles_claim) WHERE (roles_claim IS NOT NULL);

-- Name: idx_oidc_provider_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_oidc_provider_tenant ON public.oidc_provider USING btree (tenant_id);

-- Name: idx_package_created_at; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_created_at ON public.package USING btree (created_at);

-- Name: idx_package_item_content_gin; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_item_content_gin ON public.package_item USING gin (content jsonb_path_ops);

-- Name: idx_package_item_item_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_item_item_id ON public.package_item USING btree (item_id);

-- Name: idx_package_item_item_type; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_item_item_type ON public.package_item USING btree (item_type);

-- Name: idx_package_item_package_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_item_package_id ON public.package_item USING btree (package_id);

-- Name: idx_package_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_name ON public.package USING btree (name);

-- Name: idx_package_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_tenant ON public.package USING btree (tenant_id);

-- Name: idx_package_version; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_package_version ON public.package USING btree (version);

-- Name: idx_permset_field_perm_permset; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_permset_field_perm_permset ON public.permset_field_permission USING btree (permission_set_id);

-- Name: idx_permset_field_permission_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_permset_field_permission_tenant_id ON public.permset_field_permission USING btree (tenant_id);

-- Name: idx_permset_obj_perm_permset; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_permset_obj_perm_permset ON public.permset_object_permission USING btree (permission_set_id);

-- Name: idx_permset_object_permission_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_permset_object_permission_tenant_id ON public.permset_object_permission USING btree (tenant_id);

-- Name: idx_permset_sys_perm_permset; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_permset_sys_perm_permset ON public.permset_system_permission USING btree (permission_set_id);

-- Name: idx_permset_system_permission_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_permset_system_permission_tenant_id ON public.permset_system_permission USING btree (tenant_id);

-- Name: idx_permset_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_permset_tenant ON public.permission_set USING btree (tenant_id);

-- Name: idx_picklist_dependency_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_picklist_dependency_tenant_id ON public.picklist_dependency USING btree (tenant_id);

-- Name: idx_picklist_source; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_picklist_source ON public.picklist_value USING btree (picklist_source_type, picklist_source_id);

-- Name: idx_picklist_value_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_picklist_value_tenant_id ON public.picklist_value USING btree (tenant_id);

-- Name: idx_platform_user_profile; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_platform_user_profile ON public.platform_user USING btree (profile_id);

-- Name: idx_profile_field_perm_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_field_perm_collection ON public.profile_field_permission USING btree (collection_id);

-- Name: idx_profile_field_perm_profile; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_field_perm_profile ON public.profile_field_permission USING btree (profile_id);

-- Name: idx_profile_field_permission_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_field_permission_tenant_id ON public.profile_field_permission USING btree (tenant_id);

-- Name: idx_profile_obj_perm_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_obj_perm_collection ON public.profile_object_permission USING btree (collection_id);

-- Name: idx_profile_obj_perm_profile; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_obj_perm_profile ON public.profile_object_permission USING btree (profile_id);

-- Name: idx_profile_object_permission_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_object_permission_tenant_id ON public.profile_object_permission USING btree (tenant_id);

-- Name: idx_profile_sys_perm_profile; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_sys_perm_profile ON public.profile_system_permission USING btree (profile_id);

-- Name: idx_profile_system_permission_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_system_permission_tenant_id ON public.profile_system_permission USING btree (tenant_id);

-- Name: idx_profile_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_profile_tenant ON public.profile USING btree (tenant_id);

-- Name: idx_record_type_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_record_type_collection ON public.record_type USING btree (collection_id, is_active);

-- Name: idx_record_type_picklist_rt; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_record_type_picklist_rt ON public.record_type_picklist USING btree (record_type_id);

-- Name: idx_report_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_report_collection ON public.report USING btree (primary_collection_id);

-- Name: idx_report_folder; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_report_folder ON public.report USING btree (folder_id);

-- Name: idx_report_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_report_tenant ON public.report USING btree (tenant_id);

-- Name: idx_report_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_report_user ON public.report USING btree (created_by);

-- Name: idx_scheduled_job_next_run; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_scheduled_job_next_run ON public.scheduled_job USING btree (active, next_run_at);

-- Name: idx_scheduled_job_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_scheduled_job_tenant ON public.scheduled_job USING btree (tenant_id);

-- Name: idx_script_exec_log; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_script_exec_log ON public.script_execution_log USING btree (tenant_id, script_id, executed_at DESC);

-- Name: idx_script_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_script_tenant ON public.script USING btree (tenant_id);

-- Name: idx_script_trigger_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_script_trigger_collection ON public.script_trigger USING btree (collection_id, trigger_event);

-- Name: idx_script_trigger_script; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_script_trigger_script ON public.script_trigger USING btree (script_id);

-- Name: idx_search_index_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_search_index_tenant ON public.search_index USING btree (tenant_id);

-- Name: idx_search_index_vector; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_search_index_vector ON public.search_index USING gin (search_vector);

-- Name: idx_section_layout; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_section_layout ON public.layout_section USING btree (layout_id, sort_order);

-- Name: idx_security_audit_actor; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_security_audit_actor ON public.security_audit_log USING btree (actor_user_id, created_at DESC);

-- Name: idx_security_audit_category; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_security_audit_category ON public.security_audit_log USING btree (event_category, created_at DESC);

-- Name: idx_security_audit_event_type; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_security_audit_event_type ON public.security_audit_log USING btree (event_type, created_at DESC);

-- Name: idx_security_audit_target; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_security_audit_target ON public.security_audit_log USING btree (target_type, target_id, created_at DESC);

-- Name: idx_security_audit_tenant_time; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_security_audit_tenant_time ON public.security_audit_log USING btree (tenant_id, created_at DESC);

-- Name: idx_tenant_module_action_module; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_tenant_module_action_module ON public.tenant_module_action USING btree (tenant_module_id);

-- Name: idx_tenant_module_action_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_tenant_module_action_tenant_id ON public.tenant_module_action USING btree (tenant_id);

-- Name: idx_tenant_module_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_tenant_module_status ON public.tenant_module USING btree (tenant_id, status);

-- Name: idx_tenant_module_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_tenant_module_tenant ON public.tenant_module USING btree (tenant_id);

-- Name: idx_tenant_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_tenant_status ON public.tenant USING btree (status);

-- Name: idx_token_app; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_token_app ON public.connected_app_token USING btree (connected_app_id);

-- Name: idx_token_expires; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_token_expires ON public.connected_app_token USING btree (expires_at) WHERE (revoked = false);

-- Name: idx_token_hash; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_token_hash ON public.connected_app_token USING btree (token_hash);

-- Name: idx_ui_menu_item_active; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_menu_item_active ON public.ui_menu_item USING btree (active);

-- Name: idx_ui_menu_item_display_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_menu_item_display_order ON public.ui_menu_item USING btree (display_order);

-- Name: idx_ui_menu_item_menu_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_menu_item_menu_id ON public.ui_menu_item USING btree (menu_id);

-- Name: idx_ui_menu_item_menu_order; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_menu_item_menu_order ON public.ui_menu_item USING btree (menu_id, display_order);

-- Name: idx_ui_menu_item_tenant_id; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_menu_item_tenant_id ON public.ui_menu_item USING btree (tenant_id);

-- Name: idx_ui_menu_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_menu_name ON public.ui_menu USING btree (name);

-- Name: idx_ui_menu_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_menu_tenant ON public.ui_menu USING btree (tenant_id);

-- Name: idx_ui_page_active; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_page_active ON public.ui_page USING btree (active);

-- Name: idx_ui_page_config_gin; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_page_config_gin ON public.ui_page USING gin (config jsonb_path_ops);

-- Name: idx_ui_page_name; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_page_name ON public.ui_page USING btree (name);

-- Name: idx_ui_page_path; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_page_path ON public.ui_page USING btree (path);

-- Name: idx_ui_page_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_ui_page_tenant ON public.ui_page USING btree (tenant_id);

-- Name: idx_user_email; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_email ON public.platform_user USING btree (tenant_id, email);

-- Name: idx_user_group_oidc; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_group_oidc ON public.user_group USING btree (tenant_id, oidc_group_name) WHERE (oidc_group_name IS NOT NULL);

-- Name: idx_user_group_source; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_group_source ON public.user_group USING btree (tenant_id, source);

-- Name: idx_user_manager; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_manager ON public.platform_user USING btree (manager_id);

-- Name: idx_user_permset_permset; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_permset_permset ON public.user_permission_set USING btree (permission_set_id);

-- Name: idx_user_permset_user; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_permset_user ON public.user_permission_set USING btree (user_id);

-- Name: idx_user_status; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_status ON public.platform_user USING btree (tenant_id, status);

-- Name: idx_user_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_user_tenant ON public.platform_user USING btree (tenant_id);

-- Name: idx_validation_rule_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_validation_rule_collection ON public.validation_rule USING btree (collection_id, active);

-- Name: idx_validation_rule_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_validation_rule_tenant ON public.validation_rule USING btree (tenant_id);

-- Name: idx_webhook_collection; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_webhook_collection ON public.webhook USING btree (collection_id);

-- Name: idx_webhook_tenant; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_webhook_tenant ON public.webhook USING btree (tenant_id);

-- Name: search_index trig_search_index_vector; Type: TRIGGER; Schema: public; Owner: -

CREATE TRIGGER trig_search_index_vector BEFORE INSERT OR UPDATE OF search_content ON public.search_index FOR EACH ROW EXECUTE FUNCTION public.search_index_update_vector();

-- Name: approval_instance approval_instance_approval_process_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_instance
    ADD CONSTRAINT approval_instance_approval_process_id_fkey FOREIGN KEY (approval_process_id) REFERENCES public.approval_process(id);

-- Name: approval_instance approval_instance_submitted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_instance
    ADD CONSTRAINT approval_instance_submitted_by_fkey FOREIGN KEY (submitted_by) REFERENCES public.platform_user(id);

-- Name: approval_process approval_process_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_process
    ADD CONSTRAINT approval_process_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: approval_process approval_process_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_process
    ADD CONSTRAINT approval_process_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: approval_step approval_step_approval_process_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_step
    ADD CONSTRAINT approval_step_approval_process_id_fkey FOREIGN KEY (approval_process_id) REFERENCES public.approval_process(id) ON DELETE CASCADE;

-- Name: approval_step_instance approval_step_instance_approval_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_step_instance
    ADD CONSTRAINT approval_step_instance_approval_instance_id_fkey FOREIGN KEY (approval_instance_id) REFERENCES public.approval_instance(id) ON DELETE CASCADE;

-- Name: approval_step_instance approval_step_instance_assigned_to_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_step_instance
    ADD CONSTRAINT approval_step_instance_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES public.platform_user(id);

-- Name: approval_step_instance approval_step_instance_step_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.approval_step_instance
    ADD CONSTRAINT approval_step_instance_step_id_fkey FOREIGN KEY (step_id) REFERENCES public.approval_step(id);

-- Name: bulk_job bulk_job_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.bulk_job
    ADD CONSTRAINT bulk_job_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: bulk_job_result bulk_job_result_bulk_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.bulk_job_result
    ADD CONSTRAINT bulk_job_result_bulk_job_id_fkey FOREIGN KEY (bulk_job_id) REFERENCES public.bulk_job(id) ON DELETE CASCADE;

-- Name: bulk_job bulk_job_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.bulk_job
    ADD CONSTRAINT bulk_job_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: connected_app connected_app_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app
    ADD CONSTRAINT connected_app_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: connected_app_token connected_app_token_connected_app_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app_token
    ADD CONSTRAINT connected_app_token_connected_app_id_fkey FOREIGN KEY (connected_app_id) REFERENCES public.connected_app(id) ON DELETE CASCADE;

-- Name: dashboard_component dashboard_component_dashboard_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard_component
    ADD CONSTRAINT dashboard_component_dashboard_id_fkey FOREIGN KEY (dashboard_id) REFERENCES public.dashboard(id) ON DELETE CASCADE;

-- Name: dashboard_component dashboard_component_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard_component
    ADD CONSTRAINT dashboard_component_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.report(id);

-- Name: dashboard dashboard_folder_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard
    ADD CONSTRAINT dashboard_folder_id_fkey FOREIGN KEY (folder_id) REFERENCES public.report_folder(id);

-- Name: dashboard dashboard_running_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard
    ADD CONSTRAINT dashboard_running_user_id_fkey FOREIGN KEY (running_user_id) REFERENCES public.platform_user(id);

-- Name: dashboard dashboard_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard
    ADD CONSTRAINT dashboard_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: email_log email_log_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_log
    ADD CONSTRAINT email_log_template_id_fkey FOREIGN KEY (template_id) REFERENCES public.email_template(id);

-- Name: email_template email_template_related_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_template
    ADD CONSTRAINT email_template_related_collection_id_fkey FOREIGN KEY (related_collection_id) REFERENCES public.collection(id);

-- Name: email_template email_template_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_template
    ADD CONSTRAINT email_template_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: field_history field_history_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field_history
    ADD CONSTRAINT field_history_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: field_history field_history_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field_history
    ADD CONSTRAINT field_history_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: file_attachment file_attachment_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.file_attachment
    ADD CONSTRAINT file_attachment_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: file_attachment file_attachment_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.file_attachment
    ADD CONSTRAINT file_attachment_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: collection fk_collection_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection
    ADD CONSTRAINT fk_collection_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: collection fk_collection_display_field; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection
    ADD CONSTRAINT fk_collection_display_field FOREIGN KEY (display_field_id) REFERENCES public.field(id) ON DELETE SET NULL;

-- Name: collection fk_collection_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection
    ADD CONSTRAINT fk_collection_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: collection fk_collection_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection
    ADD CONSTRAINT fk_collection_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: collection_version fk_collection_version_collection; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection_version
    ADD CONSTRAINT fk_collection_version_collection FOREIGN KEY (collection_id) REFERENCES public.collection(id) ON DELETE CASCADE;

-- Name: collection_version fk_collection_version_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection_version
    ADD CONSTRAINT fk_collection_version_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: collection_version fk_collection_version_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.collection_version
    ADD CONSTRAINT fk_collection_version_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: connected_app fk_connected_app_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app
    ADD CONSTRAINT fk_connected_app_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: connected_app fk_connected_app_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.connected_app
    ADD CONSTRAINT fk_connected_app_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: dashboard_component fk_dashboard_component_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard_component
    ADD CONSTRAINT fk_dashboard_component_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: dashboard fk_dashboard_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard
    ADD CONSTRAINT fk_dashboard_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: dashboard fk_dashboard_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.dashboard
    ADD CONSTRAINT fk_dashboard_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: email_template fk_email_template_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_template
    ADD CONSTRAINT fk_email_template_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: email_template fk_email_template_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.email_template
    ADD CONSTRAINT fk_email_template_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: field fk_field_collection; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field
    ADD CONSTRAINT fk_field_collection FOREIGN KEY (collection_id) REFERENCES public.collection(id) ON DELETE CASCADE;

-- Name: field fk_field_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field
    ADD CONSTRAINT fk_field_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: field fk_field_reference_collection; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field
    ADD CONSTRAINT fk_field_reference_collection FOREIGN KEY (reference_collection_id) REFERENCES public.collection(id) ON DELETE SET NULL;

-- Name: field fk_field_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field
    ADD CONSTRAINT fk_field_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: field_version fk_field_version_collection_version; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field_version
    ADD CONSTRAINT fk_field_version_collection_version FOREIGN KEY (collection_version_id) REFERENCES public.collection_version(id) ON DELETE CASCADE;

-- Name: field_version fk_field_version_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field_version
    ADD CONSTRAINT fk_field_version_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: field_version fk_field_version_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.field_version
    ADD CONSTRAINT fk_field_version_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: file_attachment fk_file_attachment_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.file_attachment
    ADD CONSTRAINT fk_file_attachment_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: file_attachment fk_file_attachment_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.file_attachment
    ADD CONSTRAINT fk_file_attachment_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: flow fk_flow_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow
    ADD CONSTRAINT fk_flow_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: flow fk_flow_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow
    ADD CONSTRAINT fk_flow_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: global_picklist fk_global_picklist_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.global_picklist
    ADD CONSTRAINT fk_global_picklist_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: global_picklist fk_global_picklist_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.global_picklist
    ADD CONSTRAINT fk_global_picklist_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: group_membership fk_group_membership_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT fk_group_membership_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: group_permission_set fk_group_permission_set_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_permission_set
    ADD CONSTRAINT fk_group_permission_set_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: layout_field fk_layout_field_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_field
    ADD CONSTRAINT fk_layout_field_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: layout_related_list fk_layout_related_list_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_related_list
    ADD CONSTRAINT fk_layout_related_list_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: layout_section fk_layout_section_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_section
    ADD CONSTRAINT fk_layout_section_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: list_view fk_list_view_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.list_view
    ADD CONSTRAINT fk_list_view_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: list_view fk_list_view_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.list_view
    ADD CONSTRAINT fk_list_view_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: migration_run fk_migration_run_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.migration_run
    ADD CONSTRAINT fk_migration_run_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: migration_step fk_migration_step_migration_run; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.migration_step
    ADD CONSTRAINT fk_migration_step_migration_run FOREIGN KEY (migration_run_id) REFERENCES public.migration_run(id) ON DELETE CASCADE;

-- Name: note fk_note_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.note
    ADD CONSTRAINT fk_note_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: note fk_note_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.note
    ADD CONSTRAINT fk_note_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: oidc_provider fk_oidc_provider_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.oidc_provider
    ADD CONSTRAINT fk_oidc_provider_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: package_item fk_package_item_package; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.package_item
    ADD CONSTRAINT fk_package_item_package FOREIGN KEY (package_id) REFERENCES public.package(id) ON DELETE CASCADE;

-- Name: package fk_package_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.package
    ADD CONSTRAINT fk_package_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: page_layout fk_page_layout_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.page_layout
    ADD CONSTRAINT fk_page_layout_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: page_layout fk_page_layout_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.page_layout
    ADD CONSTRAINT fk_page_layout_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: permission_set fk_permission_set_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permission_set
    ADD CONSTRAINT fk_permission_set_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: permission_set fk_permission_set_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permission_set
    ADD CONSTRAINT fk_permission_set_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: permset_field_permission fk_permset_field_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_field_permission
    ADD CONSTRAINT fk_permset_field_permission_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: permset_object_permission fk_permset_object_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_object_permission
    ADD CONSTRAINT fk_permset_object_permission_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: permset_system_permission fk_permset_system_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_system_permission
    ADD CONSTRAINT fk_permset_system_permission_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: picklist_dependency fk_picklist_dependency_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_dependency
    ADD CONSTRAINT fk_picklist_dependency_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: picklist_value fk_picklist_value_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_value
    ADD CONSTRAINT fk_picklist_value_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: profile fk_profile_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile
    ADD CONSTRAINT fk_profile_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: profile_field_permission fk_profile_field_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_field_permission
    ADD CONSTRAINT fk_profile_field_permission_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: profile_object_permission fk_profile_object_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_object_permission
    ADD CONSTRAINT fk_profile_object_permission_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: profile_system_permission fk_profile_system_permission_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_system_permission
    ADD CONSTRAINT fk_profile_system_permission_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: profile fk_profile_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile
    ADD CONSTRAINT fk_profile_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: record_type fk_record_type_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT fk_record_type_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: record_type fk_record_type_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT fk_record_type_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: report fk_report_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report
    ADD CONSTRAINT fk_report_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: report_folder fk_report_folder_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report_folder
    ADD CONSTRAINT fk_report_folder_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: report_folder fk_report_folder_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report_folder
    ADD CONSTRAINT fk_report_folder_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: report fk_report_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report
    ADD CONSTRAINT fk_report_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: scheduled_job fk_scheduled_job_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.scheduled_job
    ADD CONSTRAINT fk_scheduled_job_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: scheduled_job fk_scheduled_job_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.scheduled_job
    ADD CONSTRAINT fk_scheduled_job_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: script fk_script_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script
    ADD CONSTRAINT fk_script_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: script fk_script_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script
    ADD CONSTRAINT fk_script_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: tenant_module_action fk_tenant_module_action_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant_module_action
    ADD CONSTRAINT fk_tenant_module_action_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: ui_menu_item fk_ui_menu_item_menu; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_menu_item
    ADD CONSTRAINT fk_ui_menu_item_menu FOREIGN KEY (menu_id) REFERENCES public.ui_menu(id) ON DELETE CASCADE;

-- Name: ui_menu_item fk_ui_menu_item_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_menu_item
    ADD CONSTRAINT fk_ui_menu_item_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: ui_menu fk_ui_menu_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_menu
    ADD CONSTRAINT fk_ui_menu_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: ui_page fk_ui_page_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_page
    ADD CONSTRAINT fk_ui_page_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: ui_page fk_ui_page_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_page
    ADD CONSTRAINT fk_ui_page_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: ui_page fk_ui_page_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ui_page
    ADD CONSTRAINT fk_ui_page_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: user_group fk_user_group_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group
    ADD CONSTRAINT fk_user_group_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: user_group fk_user_group_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group
    ADD CONSTRAINT fk_user_group_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: validation_rule fk_validation_rule_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.validation_rule
    ADD CONSTRAINT fk_validation_rule_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: validation_rule fk_validation_rule_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.validation_rule
    ADD CONSTRAINT fk_validation_rule_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: webhook fk_webhook_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook
    ADD CONSTRAINT fk_webhook_created_by FOREIGN KEY (created_by) REFERENCES public.platform_user(id);

-- Name: webhook fk_webhook_updated_by; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook
    ADD CONSTRAINT fk_webhook_updated_by FOREIGN KEY (updated_by) REFERENCES public.platform_user(id);

-- Name: flow_execution flow_execution_flow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_execution
    ADD CONSTRAINT flow_execution_flow_id_fkey FOREIGN KEY (flow_id) REFERENCES public.flow(id);

-- Name: flow_pending_resume flow_pending_resume_execution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_pending_resume
    ADD CONSTRAINT flow_pending_resume_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.flow_execution(id) ON DELETE CASCADE;

-- Name: flow_step_log flow_step_log_execution_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_step_log
    ADD CONSTRAINT flow_step_log_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.flow_execution(id) ON DELETE CASCADE;

-- Name: flow flow_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow
    ADD CONSTRAINT flow_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: flow_version flow_version_flow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.flow_version
    ADD CONSTRAINT flow_version_flow_id_fkey FOREIGN KEY (flow_id) REFERENCES public.flow(id) ON DELETE CASCADE;

-- Name: global_picklist global_picklist_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.global_picklist
    ADD CONSTRAINT global_picklist_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: group_membership group_membership_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT group_membership_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.user_group(id) ON DELETE CASCADE;

-- Name: group_permission_set group_permission_set_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_permission_set
    ADD CONSTRAINT group_permission_set_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.user_group(id) ON DELETE CASCADE;

-- Name: group_permission_set group_permission_set_permission_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.group_permission_set
    ADD CONSTRAINT group_permission_set_permission_set_id_fkey FOREIGN KEY (permission_set_id) REFERENCES public.permission_set(id) ON DELETE CASCADE;

-- Name: job_execution_log job_execution_log_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.job_execution_log
    ADD CONSTRAINT job_execution_log_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.scheduled_job(id);

-- Name: layout_assignment layout_assignment_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_assignment
    ADD CONSTRAINT layout_assignment_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: layout_assignment layout_assignment_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_assignment
    ADD CONSTRAINT layout_assignment_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES public.page_layout(id);

-- Name: layout_assignment layout_assignment_record_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_assignment
    ADD CONSTRAINT layout_assignment_record_type_id_fkey FOREIGN KEY (record_type_id) REFERENCES public.record_type(id);

-- Name: layout_assignment layout_assignment_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_assignment
    ADD CONSTRAINT layout_assignment_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: layout_field layout_field_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_field
    ADD CONSTRAINT layout_field_field_id_fkey FOREIGN KEY (field_id) REFERENCES public.field(id);

-- Name: layout_field layout_field_section_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_field
    ADD CONSTRAINT layout_field_section_id_fkey FOREIGN KEY (section_id) REFERENCES public.layout_section(id) ON DELETE CASCADE;

-- Name: layout_related_list layout_related_list_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_related_list
    ADD CONSTRAINT layout_related_list_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES public.page_layout(id) ON DELETE CASCADE;

-- Name: layout_related_list layout_related_list_related_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_related_list
    ADD CONSTRAINT layout_related_list_related_collection_id_fkey FOREIGN KEY (related_collection_id) REFERENCES public.collection(id);

-- Name: layout_related_list layout_related_list_relationship_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_related_list
    ADD CONSTRAINT layout_related_list_relationship_field_id_fkey FOREIGN KEY (relationship_field_id) REFERENCES public.field(id);

-- Name: layout_section layout_section_layout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.layout_section
    ADD CONSTRAINT layout_section_layout_id_fkey FOREIGN KEY (layout_id) REFERENCES public.page_layout(id) ON DELETE CASCADE;

-- Name: list_view list_view_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.list_view
    ADD CONSTRAINT list_view_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: list_view list_view_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.list_view
    ADD CONSTRAINT list_view_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: login_history login_history_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.login_history
    ADD CONSTRAINT login_history_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: login_history login_history_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.login_history
    ADD CONSTRAINT login_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.platform_user(id);

-- Name: note note_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.note
    ADD CONSTRAINT note_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: note note_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.note
    ADD CONSTRAINT note_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: observability_settings observability_settings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.observability_settings
    ADD CONSTRAINT observability_settings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: page_layout page_layout_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.page_layout
    ADD CONSTRAINT page_layout_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: page_layout page_layout_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.page_layout
    ADD CONSTRAINT page_layout_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: permission_set permission_set_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permission_set
    ADD CONSTRAINT permission_set_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: permset_field_permission permset_field_permission_permission_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_field_permission
    ADD CONSTRAINT permset_field_permission_permission_set_id_fkey FOREIGN KEY (permission_set_id) REFERENCES public.permission_set(id) ON DELETE CASCADE;

-- Name: permset_object_permission permset_object_permission_permission_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_object_permission
    ADD CONSTRAINT permset_object_permission_permission_set_id_fkey FOREIGN KEY (permission_set_id) REFERENCES public.permission_set(id) ON DELETE CASCADE;

-- Name: permset_system_permission permset_system_permission_permission_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.permset_system_permission
    ADD CONSTRAINT permset_system_permission_permission_set_id_fkey FOREIGN KEY (permission_set_id) REFERENCES public.permission_set(id) ON DELETE CASCADE;

-- Name: picklist_dependency picklist_dependency_controlling_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_dependency
    ADD CONSTRAINT picklist_dependency_controlling_field_id_fkey FOREIGN KEY (controlling_field_id) REFERENCES public.field(id);

-- Name: picklist_dependency picklist_dependency_dependent_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.picklist_dependency
    ADD CONSTRAINT picklist_dependency_dependent_field_id_fkey FOREIGN KEY (dependent_field_id) REFERENCES public.field(id);

-- Name: platform_user platform_user_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.platform_user
    ADD CONSTRAINT platform_user_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.platform_user(id);

-- Name: platform_user platform_user_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.platform_user
    ADD CONSTRAINT platform_user_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profile(id);

-- Name: platform_user platform_user_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.platform_user
    ADD CONSTRAINT platform_user_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: profile_field_permission profile_field_permission_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_field_permission
    ADD CONSTRAINT profile_field_permission_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profile(id) ON DELETE CASCADE;

-- Name: profile_object_permission profile_object_permission_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_object_permission
    ADD CONSTRAINT profile_object_permission_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profile(id) ON DELETE CASCADE;

-- Name: profile_system_permission profile_system_permission_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile_system_permission
    ADD CONSTRAINT profile_system_permission_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profile(id) ON DELETE CASCADE;

-- Name: profile profile_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.profile
    ADD CONSTRAINT profile_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: record_type record_type_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT record_type_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: record_type_picklist record_type_picklist_field_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type_picklist
    ADD CONSTRAINT record_type_picklist_field_id_fkey FOREIGN KEY (field_id) REFERENCES public.field(id);

-- Name: record_type_picklist record_type_picklist_record_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type_picklist
    ADD CONSTRAINT record_type_picklist_record_type_id_fkey FOREIGN KEY (record_type_id) REFERENCES public.record_type(id) ON DELETE CASCADE;

-- Name: record_type record_type_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT record_type_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: report report_folder_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_folder_id_fkey FOREIGN KEY (folder_id) REFERENCES public.report_folder(id);

-- Name: report_folder report_folder_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report_folder
    ADD CONSTRAINT report_folder_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: report report_primary_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_primary_collection_id_fkey FOREIGN KEY (primary_collection_id) REFERENCES public.collection(id);

-- Name: report report_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: scheduled_job scheduled_job_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.scheduled_job
    ADD CONSTRAINT scheduled_job_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: script_execution_log script_execution_log_script_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script_execution_log
    ADD CONSTRAINT script_execution_log_script_id_fkey FOREIGN KEY (script_id) REFERENCES public.script(id);

-- Name: script script_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script
    ADD CONSTRAINT script_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: script_trigger script_trigger_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script_trigger
    ADD CONSTRAINT script_trigger_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: script_trigger script_trigger_script_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.script_trigger
    ADD CONSTRAINT script_trigger_script_id_fkey FOREIGN KEY (script_id) REFERENCES public.script(id) ON DELETE CASCADE;

-- Name: search_index search_index_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.search_index
    ADD CONSTRAINT search_index_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id) ON DELETE CASCADE;

-- Name: security_audit_log security_audit_log_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.security_audit_log
    ADD CONSTRAINT security_audit_log_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: setup_audit_trail setup_audit_trail_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.setup_audit_trail
    ADD CONSTRAINT setup_audit_trail_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: setup_audit_trail setup_audit_trail_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.setup_audit_trail
    ADD CONSTRAINT setup_audit_trail_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.platform_user(id);

-- Name: tenant_module_action tenant_module_action_tenant_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant_module_action
    ADD CONSTRAINT tenant_module_action_tenant_module_id_fkey FOREIGN KEY (tenant_module_id) REFERENCES public.tenant_module(id) ON DELETE CASCADE;

-- Name: tenant_module tenant_module_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tenant_module
    ADD CONSTRAINT tenant_module_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: user_group_member user_group_member_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group_member
    ADD CONSTRAINT user_group_member_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.user_group(id) ON DELETE CASCADE;

-- Name: user_group_member user_group_member_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group_member
    ADD CONSTRAINT user_group_member_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.platform_user(id) ON DELETE CASCADE;

-- Name: user_group user_group_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_group
    ADD CONSTRAINT user_group_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: user_permission_set user_permission_set_permission_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_permission_set
    ADD CONSTRAINT user_permission_set_permission_set_id_fkey FOREIGN KEY (permission_set_id) REFERENCES public.permission_set(id) ON DELETE CASCADE;

-- Name: user_permission_set user_permission_set_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.user_permission_set
    ADD CONSTRAINT user_permission_set_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.platform_user(id) ON DELETE CASCADE;

-- Name: validation_rule validation_rule_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.validation_rule
    ADD CONSTRAINT validation_rule_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: validation_rule validation_rule_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.validation_rule
    ADD CONSTRAINT validation_rule_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: webhook webhook_collection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook
    ADD CONSTRAINT webhook_collection_id_fkey FOREIGN KEY (collection_id) REFERENCES public.collection(id);

-- Name: webhook_delivery webhook_delivery_webhook_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook_delivery
    ADD CONSTRAINT webhook_delivery_webhook_id_fkey FOREIGN KEY (webhook_id) REFERENCES public.webhook(id) ON DELETE CASCADE;

-- Name: webhook webhook_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.webhook
    ADD CONSTRAINT webhook_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id);

-- Name: approval_instance admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.approval_instance USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: approval_process admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.approval_process USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: bulk_job admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.bulk_job USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: collection admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.collection USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: connected_app admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.connected_app USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: dashboard admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.dashboard USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: dashboard_component admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.dashboard_component USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: email_log admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.email_log USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: email_template admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.email_template USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: field_history admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.field_history USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: file_attachment admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.file_attachment USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: flow admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.flow USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: flow_execution admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.flow_execution USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: global_picklist admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.global_picklist USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: group_membership admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.group_membership USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: group_permission_set admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.group_permission_set USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: layout_field admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.layout_field USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: layout_related_list admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.layout_related_list USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: layout_section admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.layout_section USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: list_view admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.list_view USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: login_history admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.login_history USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: migration_run admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.migration_run USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: note admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.note USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: oidc_provider admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.oidc_provider USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: package admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.package USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: page_layout admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.page_layout USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: permission_set admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.permission_set USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: permset_field_permission admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.permset_field_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: permset_object_permission admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.permset_object_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: permset_system_permission admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.permset_system_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: picklist_dependency admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.picklist_dependency USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: picklist_value admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.picklist_value USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: platform_user admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.platform_user USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: profile admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.profile USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: profile_field_permission admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.profile_field_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: profile_object_permission admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.profile_object_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: profile_system_permission admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.profile_system_permission USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: record_type admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.record_type USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: report admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.report USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: report_folder admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.report_folder USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: scheduled_job admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.scheduled_job USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: script admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.script USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: search_index admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.search_index USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: security_audit_log admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.security_audit_log USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: setup_audit_trail admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.setup_audit_trail USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: tenant_module_action admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.tenant_module_action USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: ui_menu admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.ui_menu USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: ui_menu_item admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.ui_menu_item USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: ui_page admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.ui_page USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: user_group admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.user_group USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: validation_rule admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.validation_rule USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: webhook admin_bypass; Type: POLICY; Schema: public; Owner: -

CREATE POLICY admin_bypass ON public.webhook USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));

-- Name: approval_instance; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.approval_instance ENABLE ROW LEVEL SECURITY;

-- Name: approval_process; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.approval_process ENABLE ROW LEVEL SECURITY;

-- Name: bulk_job; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.bulk_job ENABLE ROW LEVEL SECURITY;

-- Name: collection; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.collection ENABLE ROW LEVEL SECURITY;

-- Name: connected_app; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.connected_app ENABLE ROW LEVEL SECURITY;

-- Name: dashboard; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.dashboard ENABLE ROW LEVEL SECURITY;

-- Name: dashboard_component; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.dashboard_component ENABLE ROW LEVEL SECURITY;

-- Name: email_log; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.email_log ENABLE ROW LEVEL SECURITY;

-- Name: email_template; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.email_template ENABLE ROW LEVEL SECURITY;

-- Name: field_history; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.field_history ENABLE ROW LEVEL SECURITY;

-- Name: file_attachment; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.file_attachment ENABLE ROW LEVEL SECURITY;

-- Name: flow; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.flow ENABLE ROW LEVEL SECURITY;

-- Name: flow_execution; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.flow_execution ENABLE ROW LEVEL SECURITY;

-- Name: global_picklist; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.global_picklist ENABLE ROW LEVEL SECURITY;

-- Name: group_membership; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.group_membership ENABLE ROW LEVEL SECURITY;

-- Name: group_permission_set; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.group_permission_set ENABLE ROW LEVEL SECURITY;

-- Name: layout_field; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.layout_field ENABLE ROW LEVEL SECURITY;

-- Name: layout_related_list; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.layout_related_list ENABLE ROW LEVEL SECURITY;

-- Name: layout_section; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.layout_section ENABLE ROW LEVEL SECURITY;

-- Name: list_view; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.list_view ENABLE ROW LEVEL SECURITY;

-- Name: login_history; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.login_history ENABLE ROW LEVEL SECURITY;

-- Name: migration_run; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.migration_run ENABLE ROW LEVEL SECURITY;

-- Name: note; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.note ENABLE ROW LEVEL SECURITY;

-- Name: oidc_provider; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.oidc_provider ENABLE ROW LEVEL SECURITY;

-- Name: package; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.package ENABLE ROW LEVEL SECURITY;

-- Name: page_layout; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.page_layout ENABLE ROW LEVEL SECURITY;

-- Name: permission_set; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.permission_set ENABLE ROW LEVEL SECURITY;

-- Name: permset_field_permission; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.permset_field_permission ENABLE ROW LEVEL SECURITY;

-- Name: permset_object_permission; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.permset_object_permission ENABLE ROW LEVEL SECURITY;

-- Name: permset_system_permission; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.permset_system_permission ENABLE ROW LEVEL SECURITY;

-- Name: picklist_dependency; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.picklist_dependency ENABLE ROW LEVEL SECURITY;

-- Name: picklist_value; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.picklist_value ENABLE ROW LEVEL SECURITY;

-- Name: platform_user; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.platform_user ENABLE ROW LEVEL SECURITY;

-- Name: profile; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.profile ENABLE ROW LEVEL SECURITY;

-- Name: profile_field_permission; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.profile_field_permission ENABLE ROW LEVEL SECURITY;

-- Name: profile_object_permission; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.profile_object_permission ENABLE ROW LEVEL SECURITY;

-- Name: profile_system_permission; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.profile_system_permission ENABLE ROW LEVEL SECURITY;

-- Name: record_type; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.record_type ENABLE ROW LEVEL SECURITY;

-- Name: report; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.report ENABLE ROW LEVEL SECURITY;

-- Name: report_folder; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.report_folder ENABLE ROW LEVEL SECURITY;

-- Name: scheduled_job; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.scheduled_job ENABLE ROW LEVEL SECURITY;

-- Name: script; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.script ENABLE ROW LEVEL SECURITY;

-- Name: search_index; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.search_index ENABLE ROW LEVEL SECURITY;

-- Name: security_audit_log; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.security_audit_log ENABLE ROW LEVEL SECURITY;

-- Name: setup_audit_trail; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.setup_audit_trail ENABLE ROW LEVEL SECURITY;

-- Name: approval_instance tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.approval_instance USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: approval_process tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.approval_process USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: bulk_job tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.bulk_job USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: collection tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.collection USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: connected_app tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.connected_app USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: dashboard tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.dashboard USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: dashboard_component tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.dashboard_component USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: email_log tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.email_log USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: email_template tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.email_template USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: field_history tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.field_history USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: file_attachment tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.file_attachment USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: flow tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.flow USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: flow_execution tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.flow_execution USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: global_picklist tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.global_picklist USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: group_membership tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.group_membership USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: group_permission_set tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.group_permission_set USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: layout_field tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.layout_field USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: layout_related_list tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.layout_related_list USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: layout_section tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.layout_section USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: list_view tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.list_view USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: login_history tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.login_history USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: migration_run tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.migration_run USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: note tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.note USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: oidc_provider tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.oidc_provider USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: package tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.package USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: page_layout tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.page_layout USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: permission_set tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.permission_set USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: permset_field_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.permset_field_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: permset_object_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.permset_object_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: permset_system_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.permset_system_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: picklist_dependency tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.picklist_dependency USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: picklist_value tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.picklist_value USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: platform_user tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.platform_user USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: profile tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.profile USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: profile_field_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.profile_field_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: profile_object_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.profile_object_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: profile_system_permission tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.profile_system_permission USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: record_type tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.record_type USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: report tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.report USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: report_folder tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.report_folder USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: scheduled_job tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.scheduled_job USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: script tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.script USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: search_index tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.search_index USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: security_audit_log tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.security_audit_log USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: setup_audit_trail tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.setup_audit_trail USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: tenant_module_action tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.tenant_module_action USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: ui_menu tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.ui_menu USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: ui_menu_item tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.ui_menu_item USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: ui_page tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.ui_page USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: user_group tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.user_group USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: validation_rule tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.validation_rule USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: webhook tenant_isolation; Type: POLICY; Schema: public; Owner: -

CREATE POLICY tenant_isolation ON public.webhook USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

-- Name: tenant_module_action; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.tenant_module_action ENABLE ROW LEVEL SECURITY;

-- Name: ui_menu; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.ui_menu ENABLE ROW LEVEL SECURITY;

-- Name: ui_menu_item; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.ui_menu_item ENABLE ROW LEVEL SECURITY;

-- Name: ui_page; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.ui_page ENABLE ROW LEVEL SECURITY;

-- Name: user_group; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.user_group ENABLE ROW LEVEL SECURITY;

-- Name: validation_rule; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.validation_rule ENABLE ROW LEVEL SECURITY;

-- Name: webhook; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.webhook ENABLE ROW LEVEL SECURITY;


