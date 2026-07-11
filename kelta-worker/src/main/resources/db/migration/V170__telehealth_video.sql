-- Telehealth slice 5: video sessions on self-hosted LiveKit
-- (specs/telehealth/5-video-backend.md). Sessions are created lazily at the
-- first token request; the LiveKit webhook drives status/duration. The
-- webhook-event table gives idempotent processing without a Redis dependency.

CREATE TABLE IF NOT EXISTS video_session (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    appointment_id character varying(36),
    conversation_id character varying(36),
    room_name character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'CREATED' NOT NULL,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    duration_seconds integer,
    recording_consent boolean DEFAULT false NOT NULL,
    recording_key character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT video_session_pkey PRIMARY KEY (id),
    CONSTRAINT video_session_status_check CHECK (status IN ('CREATED', 'ACTIVE', 'ENDED')),
    CONSTRAINT video_session_room_name_key UNIQUE (room_name),
    -- A session anchors to an appointment OR an ad-hoc chat conversation.
    CONSTRAINT video_session_anchor_check
        CHECK (appointment_id IS NOT NULL OR conversation_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_video_session_appointment
    ON video_session (appointment_id) WHERE appointment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_video_session_tenant_status
    ON video_session (tenant_id, status);
-- Governor: month-window duration sums per tenant.
CREATE INDEX IF NOT EXISTS idx_video_session_tenant_ended
    ON video_session (tenant_id, ended_at) WHERE duration_seconds IS NOT NULL;

ALTER TABLE video_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY video_session FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'video_session' AND policyname = 'tenant_isolation') THEN
        CREATE POLICY tenant_isolation ON video_session
            USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'video_session' AND policyname = 'admin_bypass') THEN
        CREATE POLICY admin_bypass ON video_session
            USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));
    END IF;
END $$;

-- LiveKit webhook idempotency: one row per delivered event id; the insert IS
-- the claim (ON CONFLICT DO NOTHING → already processed). Platform-scoped
-- (webhooks arrive tenant-less), so no RLS.
CREATE TABLE IF NOT EXISTS livekit_webhook_event (
    event_id character varying(100) NOT NULL,
    event_type character varying(50) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT livekit_webhook_event_pkey PRIMARY KEY (event_id)
);
