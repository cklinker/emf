-- Telehealth slice 2: chat backend (specs/telehealth/2-chat-backend.md).
-- Four system-collection tables + RLS + indexes, and the MANAGE_CHAT system
-- permission (supervisor views over /api/chat). Statements are idempotent.
--
-- Access model: NO profile_object_permission rows are seeded — the generic
-- JSON:API routes therefore deny everyone except VIEW_ALL_DATA/MODIFY_ALL_DATA
-- holders (admins). All chat traffic flows through /api/chat/** where the
-- controller enforces participant membership; message bodies never ride
-- events or search indexes.

CREATE TABLE IF NOT EXISTS chat_queue (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chat_queue_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS chat_conversation (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    queue_id character varying(36),
    subject character varying(200),
    status character varying(20) DEFAULT 'OPEN' NOT NULL,
    origin character varying(20) DEFAULT 'INTERNAL' NOT NULL,
    assigned_to character varying(36),
    context_record_id character varying(36),
    last_message_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chat_conversation_pkey PRIMARY KEY (id),
    CONSTRAINT chat_conversation_status_check
        CHECK (status IN ('OPEN', 'ASSIGNED', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chat_conversation_origin_check
        CHECK (origin IN ('PORTAL', 'INTERNAL')),
    CONSTRAINT chat_conversation_queue_fk FOREIGN KEY (queue_id)
        REFERENCES chat_queue (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS chat_message (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    conversation_id character varying(36) NOT NULL,
    sender_id character varying(36),
    sender_type character varying(20) NOT NULL,
    kind character varying(20) DEFAULT 'TEXT' NOT NULL,
    body text NOT NULL,
    sent_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chat_message_pkey PRIMARY KEY (id),
    CONSTRAINT chat_message_sender_type_check
        CHECK (sender_type IN ('INTERNAL', 'PORTAL', 'SYSTEM')),
    CONSTRAINT chat_message_kind_check
        CHECK (kind IN ('TEXT', 'SYSTEM', 'ATTACHMENT')),
    CONSTRAINT chat_message_conversation_fk FOREIGN KEY (conversation_id)
        REFERENCES chat_conversation (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_participant (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    conversation_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    role character varying(20) NOT NULL,
    joined_at timestamp with time zone DEFAULT now() NOT NULL,
    last_read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT chat_participant_pkey PRIMARY KEY (id),
    CONSTRAINT chat_participant_role_check CHECK (role IN ('AGENT', 'PORTAL')),
    CONSTRAINT chat_participant_conversation_fk FOREIGN KEY (conversation_id)
        REFERENCES chat_conversation (id) ON DELETE CASCADE,
    CONSTRAINT chat_participant_unique UNIQUE (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_assignee
    ON chat_conversation (tenant_id, assigned_to, status);
CREATE INDEX IF NOT EXISTS idx_chat_conversation_queue
    ON chat_conversation (tenant_id, queue_id, status);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation
    ON chat_message (tenant_id, conversation_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_chat_participant_user
    ON chat_participant (tenant_id, user_id);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['chat_queue', 'chat_conversation', 'chat_message', 'chat_participant'] LOOP
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

-- MANAGE_CHAT system permission: queue/all inbox views + queue management.
-- Granted to System Administrator; every other profile gets an explicit
-- not-granted row (V162 seeding pattern). TenantProvisioningHook seeds it for
-- tenants created after this migration.
INSERT INTO profile_system_permission
    (id, tenant_id, profile_id, permission_name, granted, created_at, updated_at)
SELECT gen_random_uuid()::text,
       p.tenant_id,
       p.id,
       'MANAGE_CHAT',
       (p.is_system = true AND p.name = 'System Administrator'),
       now(), now()
FROM profile p
WHERE NOT EXISTS (SELECT 1 FROM profile_system_permission x
                  WHERE x.profile_id = p.id AND x.permission_name = 'MANAGE_CHAT');
