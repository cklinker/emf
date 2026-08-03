-- Consumer-alerting slice 1: portal billing
-- (specs/consumer-alerting/1-portal-billing.md). A tenant sells plans to its
-- PORTAL users through an external payment processor; the platform mirrors
-- subscription/pass state from verified webhooks and resolves the resulting
-- per-member entitlements. Plans carry an OPAQUE `entitlements` JSONB — the
-- platform never interprets the keys, it only merges and compares them, so a
-- tenant can invent limits without a schema change.
--
-- Money is never computed here: amounts, proration, tax, dunning and retries
-- all stay with the processor. These tables hold ids and coarse state only.

-- Plans a tenant offers. kind=DEFAULT is the free/lapsed baseline every member
-- falls back to; exactly one may be active per tenant.
CREATE TABLE IF NOT EXISTS billing_plan (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    kind character varying(20) DEFAULT 'SUBSCRIPTION' NOT NULL,
    stripe_product_id character varying(100),
    stripe_price_id character varying(100),
    entitlements jsonb,
    pass_duration_days integer,
    active boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT billing_plan_pkey PRIMARY KEY (id),
    CONSTRAINT billing_plan_kind_check
        CHECK (kind IN ('SUBSCRIPTION', 'ONE_TIME', 'DEFAULT')),
    CONSTRAINT billing_plan_code_key UNIQUE (tenant_id, code),
    -- A one-time pass is meaningless without a duration to expire it.
    CONSTRAINT billing_plan_pass_duration_check
        CHECK (kind <> 'ONE_TIME' OR pass_duration_days IS NOT NULL)
);

-- At most one active DEFAULT plan per tenant — the entitlement resolver picks
-- it by kind, so two would make the fallback nondeterministic.
CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_plan_default
    ON billing_plan (tenant_id) WHERE kind = 'DEFAULT' AND active;
-- Webhooks map a processor price id back to a plan; must be unambiguous.
CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_plan_price
    ON billing_plan (tenant_id, stripe_price_id) WHERE stripe_price_id IS NOT NULL;
-- Pricing page: active plans in display order.
CREATE INDEX IF NOT EXISTS idx_billing_plan_tenant_active
    ON billing_plan (tenant_id, active, sort_order);

-- Member <-> processor customer mapping. One customer per member per tenant.
CREATE TABLE IF NOT EXISTS billing_customer (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    stripe_customer_id character varying(100) NOT NULL,
    email character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT billing_customer_pkey PRIMARY KEY (id),
    CONSTRAINT billing_customer_user_key UNIQUE (tenant_id, user_id),
    CONSTRAINT billing_customer_stripe_key UNIQUE (tenant_id, stripe_customer_id)
);

-- Mirrored subscription state. `status` stores the processor's own vocabulary
-- verbatim (active/trialing/past_due/canceled/unpaid/incomplete*) rather than a
-- platform enum, so a new processor status degrades to "not entitled" instead
-- of failing a CHECK and dropping the webhook.
CREATE TABLE IF NOT EXISTS billing_subscription (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    plan_id character varying(36),
    stripe_subscription_id character varying(100) NOT NULL,
    stripe_customer_id character varying(100),
    status character varying(40) NOT NULL,
    current_period_end timestamp with time zone,
    cancel_at_period_end boolean DEFAULT false NOT NULL,
    canceled_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT billing_subscription_pkey PRIMARY KEY (id),
    CONSTRAINT billing_subscription_stripe_key UNIQUE (stripe_subscription_id),
    -- v1 simplification: one subscription per member; upgrades are a price
    -- change on the existing subscription via the processor's billing portal.
    CONSTRAINT billing_subscription_user_key UNIQUE (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_billing_subscription_customer
    ON billing_subscription (tenant_id, stripe_customer_id);

-- One-time passes: a bounded window of elevated entitlements bought outright.
CREATE TABLE IF NOT EXISTS billing_pass (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    plan_id character varying(36),
    stripe_checkout_session_id character varying(100) NOT NULL,
    stripe_payment_intent_id character varying(100),
    status character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    starts_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT billing_pass_pkey PRIMARY KEY (id),
    CONSTRAINT billing_pass_status_check
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REFUNDED')),
    -- The grant is idempotent on the checkout session: a redelivered webhook
    -- cannot mint a second pass.
    CONSTRAINT billing_pass_session_key UNIQUE (stripe_checkout_session_id)
);

-- Entitlement resolution: a member's live passes, newest expiry first.
CREATE INDEX IF NOT EXISTS idx_billing_pass_member
    ON billing_pass (tenant_id, user_id, status, expires_at);
-- Expiry sweep: due passes across all tenants.
CREATE INDEX IF NOT EXISTS idx_billing_pass_expiry
    ON billing_pass (status, expires_at) WHERE status = 'ACTIVE';

-- Quota enforcement as configuration: "collection X is capped by entitlement
-- key Y". The generic quota hook reads these rows, so a tenant adds a limit
-- without new code.
CREATE TABLE IF NOT EXISTS billing_entitlement_rule (
    id character varying(36) NOT NULL,
    tenant_id character varying(36) NOT NULL,
    collection_name character varying(100) NOT NULL,
    limit_key character varying(100) NOT NULL,
    count_filter jsonb,
    applies_to character varying(20) DEFAULT 'PORTAL' NOT NULL,
    message character varying(500),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT billing_entitlement_rule_pkey PRIMARY KEY (id),
    CONSTRAINT billing_entitlement_rule_applies_check
        CHECK (applies_to IN ('PORTAL', 'ALL')),
    CONSTRAINT billing_entitlement_rule_key
        UNIQUE (tenant_id, collection_name, limit_key)
);

CREATE INDEX IF NOT EXISTS idx_billing_entitlement_rule_collection
    ON billing_entitlement_rule (tenant_id, collection_name) WHERE active;

ALTER TABLE billing_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY billing_plan FORCE ROW LEVEL SECURITY;
ALTER TABLE billing_customer ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY billing_customer FORCE ROW LEVEL SECURITY;
ALTER TABLE billing_subscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY billing_subscription FORCE ROW LEVEL SECURITY;
ALTER TABLE billing_pass ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY billing_pass FORCE ROW LEVEL SECURITY;
ALTER TABLE billing_entitlement_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE ONLY billing_entitlement_rule FORCE ROW LEVEL SECURITY;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['billing_plan', 'billing_customer', 'billing_subscription',
                             'billing_pass', 'billing_entitlement_rule']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_policies
                       WHERE tablename = t AND policyname = 'tenant_isolation') THEN
            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I '
                'USING (((tenant_id)::text = current_setting(''app.current_tenant_id''::text, true)))', t);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_policies
                       WHERE tablename = t AND policyname = 'admin_bypass') THEN
            EXECUTE format(
                'CREATE POLICY admin_bypass ON %I '
                'USING ((current_setting(''app.current_tenant_id''::text, true) = ''''::text))', t);
        END IF;
    END LOOP;
END $$;

-- Webhook idempotency: the insert IS the claim (ON CONFLICT DO NOTHING =>
-- already processed). Platform-scoped — a webhook arrives before any tenant
-- context exists — so no RLS, matching livekit_webhook_event.
CREATE TABLE IF NOT EXISTS billing_webhook_event (
    event_id character varying(100) NOT NULL,
    tenant_id character varying(36),
    event_type character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT billing_webhook_event_pkey PRIMARY KEY (event_id)
);

-- MANAGE_BILLING: administer plans and entitlement rules. Granted only to
-- System Administrator on existing tenants — billing config is money-adjacent,
-- so it does NOT follow the V162 "every built-in profile" backfill. Idempotent.
INSERT INTO profile_system_permission
    (id, tenant_id, profile_id, permission_name, granted, created_at, updated_at)
SELECT
    gen_random_uuid()::text,
    p.tenant_id,
    p.id,
    'MANAGE_BILLING',
    (p.is_system = true AND p.name = 'System Administrator'),
    now(), now()
FROM profile p
WHERE NOT EXISTS (SELECT 1 FROM profile_system_permission x
                  WHERE x.profile_id = p.id
                    AND x.permission_name = 'MANAGE_BILLING');
