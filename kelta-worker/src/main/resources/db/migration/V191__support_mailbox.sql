-- Support mailbox slice 1: schema (specs/support-mailbox/README.md).
-- Six system-collection tables + RLS + indexes, and the VIEW_SUPPORT_MAILBOX /
-- MANAGE_SUPPORT_MAILBOX system permissions. Statements are idempotent.
--
-- Access model: NO profile_object_permission rows are seeded — the generic
-- JSON:API routes therefore deny everyone except VIEW_ALL_DATA/MODIFY_ALL_DATA
-- holders (admins). All mailbox traffic flows through /api/support/** where the
-- controller enforces mailbox_access membership. This matters more here than it
-- did for chat (V168, same decision): inbound message bodies are third-party
-- HTML, and the admin Resource Browser renders collection rows with no iframe.
--
-- Permission split: VIEW_SUPPORT_MAILBOX opens the console at all;
-- MANAGE_SUPPORT_MAILBOX covers config, membership, SLA policy, the cross-mailbox
-- "all" view and raw-MIME download. Authority to APPROVE a reply is deliberately
-- neither of them — it is the per-mailbox mailbox_access.role, because a global
-- "may approve" would apply to mailboxes the holder is not a member of.


-- ---------------------------------------------------------------------------
-- mailbox — the first-class entity, and the inbound ingress trust anchor.
--
-- chat_queue (V168:11-22) carries name/description/active and nothing else,
-- because chat's entry point is authenticated and its policy is implicit. A
-- mailbox is three things a chat queue is not: an UNAUTHENTICATED ingress (so it
-- must carry its own trust anchor), a POLICY HOLDER (SLA and auto-reply inputs),
-- and an OUTBOUND IDENTITY (what a reply is sent as, and where replies come back).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description text,

    -- The address customers write to. Display and reply-From only: routing is by
    -- webhook_key alone, never by any address in the payload (see below).
    address character varying(320) NOT NULL,
    reply_from_address character varying(320),
    reply_from_name character varying(200),
    -- Reply-To base for VERP thread tokens: support+t<threadId>.<hmac8>@domain
    verp_domain character varying(255),

    -- ---------- Inbound endpoint identity ----------
    -- 32 bytes of SecureRandom, base64url. An IDENTIFIER, never a secret: it
    -- appears in provider consoles, access logs and support tickets.
    -- Authentication is separate (below) and always required.
    webhook_key character varying(64) NOT NULL,
    inbound_provider character varying(30) DEFAULT 'SES_SNS' NOT NULL,
    -- SES only. A valid SNS signature proves "some SNS topic in some AWS account
    -- signed this" — anyone can create a topic and publish to a public endpoint.
    -- Pinning the ARN is what makes it OUR topic.
    provider_topic_arn character varying(500),
    -- HMAC shared secret lives in the credential vault, never in a column here.
    -- Two slots so rotation can overlap (V186:12-17 multi-active-key rationale).
    inbound_secret_credential_id character varying(36),
    inbound_prev_secret_credential_id character varying(36),
    inbound_prev_secret_expires_at timestamp with time zone,
    inbound_secret_hint character varying(12),
    inbound_secret_rotated_at timestamp with time zone,
    -- Comma-separated CIDRs. The only trust anchor available for providers with
    -- no signature scheme at all (Postmark inbound authenticates by URL secret).
    inbound_allowed_cidrs text,

    -- ---------- Ingest limits ----------
    max_message_bytes bigint DEFAULT 26214400 NOT NULL,
    max_attachments integer DEFAULT 20 NOT NULL,
    max_attachment_bytes bigint DEFAULT 26214400 NOT NULL,
    -- 0 disables subject-fallback threading entirely.
    subject_threading_days integer DEFAULT 7 NOT NULL,

    -- ---------- SLA POLICY (inputs, not state — state lives on the thread) ----------
    sla_first_response_minutes integer,
    sla_resolution_minutes integer,
    sla_risk_threshold_pct integer DEFAULT 75 NOT NULL,
    -- Shape: {"mon": [["09:00","17:00"]], ...}. Carried from slice 1 so enabling
    -- business-hours evaluation later needs no migration; v1 computes flat
    -- wall-clock durations and ignores this column.
    business_hours jsonb,
    business_timezone character varying(64) DEFAULT 'UTC' NOT NULL,
    escalation_user_id character varying(36),
    escalation_group_id character varying(36),

    -- ---------- Triage / reply policy ----------
    -- Every automation default is OFF. A mailbox created today behaves as a
    -- plain human inbox until someone deliberately turns something on.
    auto_reply_enabled boolean DEFAULT false NOT NULL,
    auto_reply_min_confidence numeric(4,3) DEFAULT 0.900 NOT NULL,
    max_auto_replies_per_thread integer DEFAULT 2 NOT NULL,
    ai_draft_enabled boolean DEFAULT false NOT NULL,
    require_verified_sender_for_account_data boolean DEFAULT true NOT NULL,

    default_assignee_id character varying(36),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_webhook_key_key UNIQUE (webhook_key),
    CONSTRAINT mailbox_address_key UNIQUE (tenant_id, address),
    CONSTRAINT mailbox_provider_check CHECK (inbound_provider IN
        ('SES_SNS', 'SES_SNS_INLINE', 'GENERIC_HMAC', 'POSTMARK', 'MAILGUN', 'CLOUDMAILIN')),
    CONSTRAINT mailbox_risk_threshold_check CHECK (sla_risk_threshold_pct BETWEEN 1 AND 99)
);

-- DELIBERATELY NOT tenant-leading, unlike every other unique key in the schema.
-- Webhook resolution runs BEFORE any tenant is known — it is what determines the
-- tenant — so a (tenant_id, webhook_key) index could not serve it. Do not "fix".
COMMENT ON CONSTRAINT mailbox_webhook_key_key ON mailbox IS
    'Globally unique, not tenant-scoped: resolution happens before a tenant is bound.';


-- ---------------------------------------------------------------------------
-- mailbox_access — membership, hoisted to the mailbox.
--
-- chat_participant (V168:70-86) scopes membership per CONVERSATION, because a
-- chat has a distinct cast each time. A shared mailbox is the exact inverse:
-- access is granted once and inherited by every thread — that inheritance is
-- what "shared" means. Per-thread rows would mean N writes per inbound mail and
-- would turn "who can see this inbox" into an aggregation instead of a lookup.
--
-- No last_read_at here (chat_participant has one): the per-(user, thread) unread
-- fact has no home on a per-mailbox row. It gets its own table in the console
-- slice, where it is actually needed.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_access (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,
    principal_type character varying(10) NOT NULL,
    principal_id character varying(36) NOT NULL,
    -- VIEWER reads; AGENT replies; MANAGER also approves drafts, reassigns and
    -- configures. Approval authority is here rather than a system permission so
    -- it cannot leak across mailboxes.
    role character varying(10) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_access_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_access_type_check CHECK (principal_type IN ('USER', 'GROUP')),
    CONSTRAINT mailbox_access_role_check CHECK (role IN ('VIEWER', 'AGENT', 'MANAGER')),
    CONSTRAINT mailbox_access_unique UNIQUE (mailbox_id, principal_type, principal_id),
    CONSTRAINT mailbox_access_mailbox_fk FOREIGN KEY (mailbox_id)
        REFERENCES mailbox (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- mailbox_thread — the conversation, and the sole owner of SLA state.
--
-- Same spine as chat_conversation (V168:24-46) — status / assigned_to /
-- last_message_at / closed_at — plus three things chat has no need for: an
-- EXTERNAL requester (a chat participant is always a platform identity),
-- threading keys, and SLA.
--
-- Why SLA state lives here and not on mailbox:
--   1. The promise is per-conversation and must FREEZE. Due times are computed
--      once at thread creation and stored absolute. Deriving them at read from
--      mailbox.sla_first_response_minutes would mean an admin editing that field
--      retroactively breaches or un-breaches every open thread and rewrites
--      history in every SLA report already run.
--   2. The sweep is a single indexed cross-tenant scan. A join to mailbox plus
--      per-row interval arithmetic is not indexable.
--   3. On the message it has no home: "answered 4 minutes late" is a fact about
--      the conversation, and must survive message redaction and retention.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_thread (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,
    subject character varying(500),
    -- Re:/Fwd:/AW: stripped, whitespace collapsed, lowercased. Subject-fallback
    -- threading join key.
    normalized_subject character varying(255),
    status character varying(24) DEFAULT 'OPEN' NOT NULL,
    priority character varying(10) DEFAULT 'NORMAL' NOT NULL,
    assigned_to character varying(36),

    -- ---------- Requester identity, and the disclosure gate ----------
    requester_email character varying(320) NOT NULL,
    requester_name character varying(200),
    -- FALSE by default, raised only by a DMARC-aligned identity match, an
    -- explicit challenge sent to an address ALREADY on the account record, or an
    -- audited manual action. THIS COLUMN — not a prompt instruction — is the
    -- "never disclose account data to an unverified sender" control.
    requester_verified boolean DEFAULT false NOT NULL,
    requester_identity_id character varying(36),
    verification_method character varying(20),

    -- ---------- Triage ----------
    category character varying(60),
    category_confidence numeric(4,3),
    triaged_at timestamp with time zone,
    -- Header-independent loop breaker. Auto-Submitted and Precedence are
    -- advisory and frequently absent; a counter is not.
    auto_reply_count integer DEFAULT 0 NOT NULL,

    -- ---------- Activity ----------
    message_count integer DEFAULT 0 NOT NULL,
    last_message_at timestamp with time zone,
    last_inbound_at timestamp with time zone,
    last_outbound_at timestamp with time zone,
    first_response_at timestamp with time zone,
    resolved_at timestamp with time zone,
    closed_at timestamp with time zone,
    -- Reopen-after-close linkage. A late reply opens a NEW thread rather than
    -- reviving a closed one, so the SLA clock restarts honestly instead of
    -- arriving pre-breached.
    parent_thread_id character varying(36),

    -- ---------- SLA STATE ----------
    sla_first_response_due_at timestamp with time zone,
    sla_first_response_state character varying(10) DEFAULT 'NONE' NOT NULL,
    sla_resolution_due_at timestamp with time zone,
    sla_resolution_state character varying(10) DEFAULT 'NONE' NOT NULL,
    sla_paused_at timestamp with time zone,
    sla_paused_ms bigint DEFAULT 0 NOT NULL,
    -- Sweep CLAIM columns — the telehealth_appointment.reminder_sent_at idiom
    -- (AppointmentReminderSweep:72-97). An atomic UPDATE ... RETURNING on these
    -- makes the sweep multi-pod safe with no leader election.
    sla_risk_notified_at timestamp with time zone,
    sla_breach_notified_at timestamp with time zone,

    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_thread_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_thread_status_check CHECK (status IN
        ('OPEN', 'ASSIGNED', 'WAITING_ON_CUSTOMER', 'WAITING_ON_APPROVAL',
         'RESOLVED', 'CLOSED', 'SPAM', 'ARCHIVED')),
    CONSTRAINT mailbox_thread_priority_check CHECK (priority IN
        ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT mailbox_thread_sla_fr_check CHECK (sla_first_response_state IN
        ('NONE', 'PENDING', 'AT_RISK', 'BREACHED', 'MET')),
    CONSTRAINT mailbox_thread_sla_res_check CHECK (sla_resolution_state IN
        ('NONE', 'PENDING', 'AT_RISK', 'BREACHED', 'MET')),
    CONSTRAINT mailbox_thread_verification_check CHECK (verification_method IS NULL
        OR verification_method IN ('DMARC_MATCH', 'CHALLENGE', 'MANUAL')),
    CONSTRAINT mailbox_thread_mailbox_fk FOREIGN KEY (mailbox_id)
        REFERENCES mailbox (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- mailbox_message
--
-- Same spine as chat_message (V168:48-67). Every addition traces to one fact:
-- email is a federated protocol with an identity story chat does not have.
-- message_id/in_reply_to/references_header are the threading keys; the five
-- verdicts are the trust evidence; auto_submitted/precedence/is_bulk/is_bounce
-- are extracted ONCE at parse so loop decisions are a column read rather than a
-- re-parse; raw_storage_key points at the immutable MIME, the only thing that
-- can settle a "we never got that" dispute.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_message (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    thread_id character varying(36) NOT NULL,
    -- Denormalized so per-mailbox counts and quotas need no join.
    mailbox_id character varying(36) NOT NULL,
    -- Replaces chat's sender_type: an inbound sender may be no platform identity.
    direction character varying(10) NOT NULL,
    kind character varying(12) DEFAULT 'EMAIL' NOT NULL,

    -- ---------- RFC 5322 identity: the threading keys ----------
    message_id character varying(998),
    in_reply_to character varying(998),
    references_header text,

    from_address character varying(320),
    from_name character varying(200),
    to_addresses text,
    cc_addresses text,
    reply_to_address character varying(320),
    subject character varying(500),

    body_text text,
    -- Stored AS RECEIVED — this column is evidence, not display. It is never
    -- rendered without sanitisation, and is deliberately not declared on the
    -- system collection, so it cannot reach the admin Resource Browser.
    body_html text,
    body_html_sanitized text,
    snippet character varying(500),
    -- Selected headers only, never the full set.
    headers jsonb,

    -- ---------- Authentication verdicts (null for providers without them) ----------
    spf_result character varying(20),
    dkim_result character varying(20),
    dmarc_result character varying(20),
    dmarc_policy character varying(20),
    spam_verdict character varying(20),
    virus_verdict character varying(20),

    -- ---------- Loop-prevention signals, extracted at parse time ----------
    auto_submitted character varying(40),
    precedence character varying(20),
    is_bulk boolean DEFAULT false NOT NULL,
    is_bounce boolean DEFAULT false NOT NULL,

    raw_storage_key character varying(500),
    raw_size_bytes bigint,
    -- OUTBOUND / NOTE only.
    author_user_id character varying(36),
    email_log_id character varying(36),
    delivery_status character varying(20),
    sent_at timestamp with time zone,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_message_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_message_direction_check CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT mailbox_message_kind_check CHECK (kind IN ('EMAIL', 'NOTE', 'SYSTEM')),
    CONSTRAINT mailbox_message_delivery_check CHECK (delivery_status IS NULL
        OR delivery_status IN ('QUEUED', 'SENT', 'FAILED', 'SUPPRESSED')),
    CONSTRAINT mailbox_message_thread_fk FOREIGN KEY (thread_id)
        REFERENCES mailbox_thread (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- mailbox_attachment
--
-- Not file_attachment (V1:1022-1037): that table is keyed by a NOT NULL
-- (collection_id, record_id) pair, so reuse would mean minting a synthetic
-- collection id and inheriting the generic attachment permission surface and
-- AttachmentUploadController's pending-row lifecycle. It also has no
-- content_id/inline (cid: rendering) and no checksum (redelivery dedupe).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_attachment (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    message_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,
    -- RFC 2047/2231 decoded, path-stripped, bidi-override stripped.
    filename character varying(500) NOT NULL,
    -- Determined by our own sniffing, never taken from the MIME part header.
    content_type character varying(200) NOT NULL,
    size_bytes bigint DEFAULT 0 NOT NULL,
    content_id character varying(255),
    inline boolean DEFAULT false NOT NULL,
    -- MUST begin '<tenantId>/' or /api/files/** cannot serve it: FileController
    -- authorizes by comparing the first key segment to X-Cerbos-Scope.
    storage_key character varying(500),
    checksum_sha256 character varying(64),
    scan_status character varying(20) DEFAULT 'UNKNOWN' NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_attachment_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_attachment_scan_check CHECK (scan_status IN
        ('UNKNOWN', 'CLEAN', 'INFECTED', 'SKIPPED')),
    CONSTRAINT mailbox_attachment_message_fk FOREIGN KEY (message_id)
        REFERENCES mailbox_message (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- mailbox_inbound_event — the idempotency ledger.
--
-- The chat schema has no analogue and does not need one. This is NOT optional:
-- SNS retries on any non-2xx and delivers at-least-once even on 2xx, so without
-- a claim table one customer email becomes several threads and several
-- auto-replies.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mailbox_inbound_event (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    mailbox_id character varying(36) NOT NULL,
    provider character varying(30) NOT NULL,
    -- SNS MessageId / Postmark MessageID / Mailgun token. Null where none exists.
    provider_event_id character varying(255),
    -- SHA-256 hex of the raw request body. Dedupe key of last resort.
    payload_digest character varying(64) NOT NULL,
    status character varying(20) DEFAULT 'RECEIVED' NOT NULL,
    reject_reason character varying(200),
    message_id character varying(36),
    raw_storage_key character varying(500),
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    processed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT mailbox_inbound_event_pkey PRIMARY KEY (id),
    CONSTRAINT mailbox_inbound_event_status_check CHECK (status IN
        ('RECEIVED', 'PARSED', 'ROUTED', 'REJECTED', 'DUPLICATE', 'FAILED'))
);

-- The hard dedupe key, used whenever the provider supplies a stable event id.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mailbox_inbound_event_provider
    ON mailbox_inbound_event (tenant_id, provider, provider_event_id)
    WHERE provider_event_id IS NOT NULL;

-- Fallback ONLY when it does not. Deliberately partial: a globally unique digest
-- would silently drop a customer legitimately re-sending an identical message.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mailbox_inbound_event_digest
    ON mailbox_inbound_event (tenant_id, mailbox_id, payload_digest)
    WHERE provider_event_id IS NULL;


-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------

-- The inbox list: the hot query.
CREATE INDEX IF NOT EXISTS idx_mailbox_thread_inbox
    ON mailbox_thread (tenant_id, mailbox_id, status, last_message_at DESC);
-- Mirrors idx_chat_conversation_assignee (V168:89-90).
CREATE INDEX IF NOT EXISTS idx_mailbox_thread_assignee
    ON mailbox_thread (tenant_id, assigned_to, status);
-- Subject-fallback threading, and "everything from this person".
CREATE INDEX IF NOT EXISTS idx_mailbox_thread_requester
    ON mailbox_thread (tenant_id, mailbox_id, requester_email, last_message_at DESC);

-- SLA sweep indexes. DELIBERATELY NOT tenant-leading, like mailbox_webhook_key_key
-- above: the sweep runs cross-tenant on the scheduler thread with NO tenant bound,
-- riding the admin_bypass policy, so a tenant-leading index cannot serve it.
-- Partial, because only PENDING rows are ever swept. Do not "fix" these either.
CREATE INDEX IF NOT EXISTS idx_mailbox_thread_sla_first
    ON mailbox_thread (sla_first_response_due_at)
    WHERE sla_first_response_state = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_mailbox_thread_sla_resolution
    ON mailbox_thread (sla_resolution_due_at)
    WHERE sla_resolution_state = 'PENDING';

-- Mirrors idx_chat_message_conversation (V168:93-94).
CREATE INDEX IF NOT EXISTS idx_mailbox_message_thread
    ON mailbox_message (tenant_id, thread_id, sent_at);
-- In-Reply-To / References resolution.
CREATE INDEX IF NOT EXISTS idx_mailbox_message_msgid
    ON mailbox_message (tenant_id, mailbox_id, message_id)
    WHERE message_id IS NOT NULL;
-- Mirrors idx_chat_participant_user (V168:95-96): "which mailboxes can I see".
CREATE INDEX IF NOT EXISTS idx_mailbox_access_principal
    ON mailbox_access (tenant_id, principal_type, principal_id);
CREATE INDEX IF NOT EXISTS idx_mailbox_attachment_message
    ON mailbox_attachment (tenant_id, message_id);


-- ---------------------------------------------------------------------------
-- RLS — tenant_isolation + admin_bypass on all six, per V168:98-112.
-- admin_bypass is what lets the cross-tenant SLA sweep run with no tenant bound.
-- ---------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['mailbox', 'mailbox_access', 'mailbox_thread',
                             'mailbox_message', 'mailbox_attachment',
                             'mailbox_inbound_event'] LOOP
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


-- ---------------------------------------------------------------------------
-- System permissions. Granted to System Administrator; every other profile gets
-- an explicit not-granted row (V162/V168 seeding pattern). TenantProvisioningHook
-- seeds them for tenants created after this migration.
-- ---------------------------------------------------------------------------
INSERT INTO profile_system_permission
    (id, tenant_id, profile_id, permission_name, granted, created_at, updated_at)
SELECT gen_random_uuid()::text,
       p.tenant_id,
       p.id,
       'VIEW_SUPPORT_MAILBOX',
       (p.is_system = true AND p.name = 'System Administrator'),
       now(), now()
FROM profile p
WHERE NOT EXISTS (SELECT 1 FROM profile_system_permission x
                  WHERE x.profile_id = p.id AND x.permission_name = 'VIEW_SUPPORT_MAILBOX');

INSERT INTO profile_system_permission
    (id, tenant_id, profile_id, permission_name, granted, created_at, updated_at)
SELECT gen_random_uuid()::text,
       p.tenant_id,
       p.id,
       'MANAGE_SUPPORT_MAILBOX',
       (p.is_system = true AND p.name = 'System Administrator'),
       now(), now()
FROM profile p
WHERE NOT EXISTS (SELECT 1 FROM profile_system_permission x
                  WHERE x.profile_id = p.id AND x.permission_name = 'MANAGE_SUPPORT_MAILBOX');
