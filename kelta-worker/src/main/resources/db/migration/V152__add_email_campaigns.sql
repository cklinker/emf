-- V152: Mass-email campaigns on top of the existing SMTP send path.
--
-- Adds three tenant-scoped tables:
--   * email_campaign            — one row per campaign (config + aggregate stats). Backs the
--                                 read-only "campaigns" system collection; mutations go through
--                                 CampaignAdminController (gated on MANAGE_CAMPAIGNS).
--   * email_campaign_recipient  — one row per resolved recipient (per-send + tracking events).
--   * email_suppression         — per-tenant unsubscribe / suppression list. A (tenant, email)
--                                 here suppresses all future campaign sends.
--
-- The campaign runner (CampaignRunnerService) polls email_campaign with a conditional-claim
-- UPDATE (mirrors BulkJobProcessorService's SELECT FOR UPDATE SKIP LOCKED leader election),
-- resolves recipients from the target collection via the QueryEngine, renders per recipient,
-- and sends through the existing EmailService. Tracking pixel / click-redirect / unsubscribe
-- endpoints (public, HMAC-token authenticated) record events back onto the recipient rows.
--
-- RLS: tenant isolation + admin_bypass (empty tenant setting), matching quick_action (V151).
-- The scheduled poller runs with no tenant in context (empty setting -> admin_bypass) so it can
-- claim campaigns across tenants; per-campaign work then runs under TenantContext.withTenant.

-- ---------------------------------------------------------------------------
-- email_campaign
-- ---------------------------------------------------------------------------
CREATE TABLE email_campaign (
    id                    VARCHAR(36)  PRIMARY KEY,
    tenant_id             VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    name                  VARCHAR(200) NOT NULL,
    description           VARCHAR(500),
    subject               VARCHAR(500) NOT NULL,
    body_html             TEXT,
    template_id           VARCHAR(36),
    target_collection     VARCHAR(100) NOT NULL,
    recipient_email_field VARCHAR(100) NOT NULL,
    filter_json           JSONB,
    list_view_id          VARCHAR(36),
    from_name             VARCHAR(200),
    from_address          VARCHAR(320),
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    scheduled_at          TIMESTAMP WITH TIME ZONE,
    total_recipients      INTEGER      NOT NULL DEFAULT 0,
    sent_count            INTEGER      NOT NULL DEFAULT 0,
    failed_count          INTEGER      NOT NULL DEFAULT 0,
    open_count            INTEGER      NOT NULL DEFAULT 0,
    click_count           INTEGER      NOT NULL DEFAULT 0,
    unsubscribe_count     INTEGER      NOT NULL DEFAULT 0,
    started_at            TIMESTAMP WITH TIME ZONE,
    completed_at          TIMESTAMP WITH TIME ZONE,
    error_message         TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(36),
    updated_by            VARCHAR(36),
    CONSTRAINT chk_campaign_status CHECK (status IN
        ('DRAFT','SCHEDULED','QUEUED','SENDING','SENT','FAILED','CANCELLED'))
);

-- Poller hot path: claimable campaigns (QUEUED, or SCHEDULED whose time has arrived).
CREATE INDEX idx_email_campaign_claim  ON email_campaign (status, scheduled_at);
CREATE INDEX idx_email_campaign_tenant ON email_campaign (tenant_id, created_at DESC);

ALTER TABLE email_campaign ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_campaign FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON email_campaign;
DROP POLICY IF EXISTS admin_bypass     ON email_campaign;
CREATE POLICY tenant_isolation ON email_campaign
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON email_campaign
    USING (current_setting('app.current_tenant_id', true) = '');

-- ---------------------------------------------------------------------------
-- email_campaign_recipient
-- ---------------------------------------------------------------------------
CREATE TABLE email_campaign_recipient (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    campaign_id     VARCHAR(36)  NOT NULL REFERENCES email_campaign(id) ON DELETE CASCADE,
    record_id       VARCHAR(36),
    email           VARCHAR(320) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    email_log_id    VARCHAR(36),
    open_count      INTEGER      NOT NULL DEFAULT 0,
    click_count     INTEGER      NOT NULL DEFAULT 0,
    sent_at         TIMESTAMP WITH TIME ZONE,
    opened_at       TIMESTAMP WITH TIME ZONE,
    clicked_at      TIMESTAMP WITH TIME ZONE,
    unsubscribed_at TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    updated_by      VARCHAR(36),
    CONSTRAINT chk_recipient_status CHECK (status IN
        ('PENDING','SENT','FAILED','SKIPPED','SUPPRESSED')),
    CONSTRAINT uq_campaign_recipient UNIQUE (campaign_id, email)
);

CREATE INDEX idx_campaign_recipient_campaign ON email_campaign_recipient (campaign_id, status);

ALTER TABLE email_campaign_recipient ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_campaign_recipient FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON email_campaign_recipient;
DROP POLICY IF EXISTS admin_bypass     ON email_campaign_recipient;
CREATE POLICY tenant_isolation ON email_campaign_recipient
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON email_campaign_recipient
    USING (current_setting('app.current_tenant_id', true) = '');

-- ---------------------------------------------------------------------------
-- email_suppression
-- ---------------------------------------------------------------------------
CREATE TABLE email_suppression (
    id          VARCHAR(36)  PRIMARY KEY,
    tenant_id   VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    email       VARCHAR(320) NOT NULL,
    reason      VARCHAR(30)  NOT NULL DEFAULT 'UNSUBSCRIBE',
    campaign_id VARCHAR(36),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(36),
    updated_by  VARCHAR(36),
    CONSTRAINT chk_suppression_reason CHECK (reason IN
        ('UNSUBSCRIBE','BOUNCE','COMPLAINT','MANUAL')),
    CONSTRAINT uq_suppression_tenant_email UNIQUE (tenant_id, email)
);

ALTER TABLE email_suppression ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_suppression FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON email_suppression;
DROP POLICY IF EXISTS admin_bypass     ON email_suppression;
CREATE POLICY tenant_isolation ON email_suppression
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON email_suppression
    USING (current_setting('app.current_tenant_id', true) = '');

-- ---------------------------------------------------------------------------
-- Permission: MANAGE_CAMPAIGNS
-- Clone the existing MANAGE_EMAIL_TEMPLATES grants onto MANAGE_CAMPAIGNS for every existing
-- profile (System Administrator + Marketing User get it granted; others get it denied), so the
-- new permission inherits the same "who can touch email" posture. New tenants seed it via
-- TenantProvisioningHook.
-- ---------------------------------------------------------------------------
INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
SELECT gen_random_uuid()::text, p.profile_id, 'MANAGE_CAMPAIGNS', p.granted
FROM profile_system_permission p
WHERE p.permission_name = 'MANAGE_EMAIL_TEMPLATES'
  AND NOT EXISTS (
      SELECT 1 FROM profile_system_permission x
      WHERE x.profile_id = p.profile_id
        AND x.permission_name = 'MANAGE_CAMPAIGNS'
  );
