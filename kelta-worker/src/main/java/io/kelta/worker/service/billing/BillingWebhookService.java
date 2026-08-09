package io.kelta.worker.service.billing;

import io.kelta.runtime.event.BillingEntitlementChangedPayload;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.worker.repository.BillingCustomerRepository;
import io.kelta.worker.repository.BillingPassRepository;
import io.kelta.worker.repository.BillingPlan;
import io.kelta.worker.repository.BillingPlanRepository;
import io.kelta.worker.repository.BillingSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Applies verified payment-processor webhooks to the mirrored billing tables.
 *
 * <p><b>Idempotency.</b> The claim insert and the mutation share a single
 * {@link Transactional} method: if processing throws, the claim rolls back with
 * it and the processor's retry gets a real second attempt. Claiming in a
 * separate transaction would mark an event processed that never was —
 * a deliberate improvement on the older webhook handlers in this codebase.
 *
 * <p><b>Trust.</b> The signature is verified by the caller before anything here
 * runs; the {@code tenantId} in the request path is untrusted and only selects
 * which signing secret to verify against, so a passing HMAC is what proves the
 * event belongs to that tenant.
 *
 * <p><b>Unknown events are claimed and ignored</b> rather than rejected, so the
 * processor stops retrying something this platform will never act on.
 */
@Service
public class BillingWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookService.class);

    static final String SUBJECT_PREFIX = "kelta.billing.entitlement.changed.";
    static final String EVENT_TYPE = "kelta.billing.entitlement.changed";
    static final String TRIGGER_SUBJECT_PREFIX = "kelta.trigger.";
    static final String TRIGGER_TOPIC = "billing.subscription";

    private final JdbcTemplate jdbcTemplate;
    private final BillingCustomerRepository customerRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPassRepository passRepository;
    private final BillingPlanRepository planRepository;
    private final EntitlementService entitlementService;
    private final PlatformEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public BillingWebhookService(JdbcTemplate jdbcTemplate,
                                 BillingCustomerRepository customerRepository,
                                 BillingSubscriptionRepository subscriptionRepository,
                                 BillingPassRepository passRepository,
                                 BillingPlanRepository planRepository,
                                 EntitlementService entitlementService,
                                 PlatformEventPublisher eventPublisher,
                                 ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passRepository = passRepository;
        this.planRepository = planRepository;
        this.entitlementService = entitlementService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes one verified event.
     *
     * @return false only when the event was a duplicate (already claimed); true
     *         when it was applied or deliberately ignored. Either way the caller
     *         answers 200 — the processor must not retry a duplicate.
     */
    @Transactional
    public boolean process(String tenantId, String rawBody) {
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (RuntimeException e) {
            log.warn("Unparseable billing webhook body for tenant {}: {}", tenantId, e.getMessage());
            return true; // Never retry a body we can't read.
        }

        String eventId = text(event, "id");
        String eventType = text(event, "type");
        if (eventId == null || eventType == null) {
            log.warn("Billing webhook missing id or type for tenant {}", tenantId);
            return true;
        }

        // The claim IS the idempotency check, and it shares this transaction with
        // the mutation below.
        if (!claim(eventId, tenantId, eventType)) {
            log.debug("Duplicate billing webhook {} ({}) — skipping", eventId, eventType);
            return false;
        }

        JsonNode object = event.path("data").path("object");
        switch (eventType) {
            case "checkout.session.completed" -> handleCheckoutCompleted(tenantId, object);
            case "customer.subscription.created", "customer.subscription.updated" ->
                    handleSubscriptionUpsert(tenantId, object);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(tenantId, object);
            case "invoice.paid", "invoice.payment_failed" ->
                    // No mirrored state to change; tenants react via flows.
                    bridgeToFlows(tenantId, eventType, object);
            default -> log.debug("Ignoring unhandled billing event type {}", eventType);
        }
        return true;
    }

    private boolean claim(String eventId, String tenantId, String eventType) {
        return jdbcTemplate.update(
                "INSERT INTO billing_webhook_event (event_id, tenant_id, event_type, processed_at) "
                        + "VALUES (?, ?, ?, NOW()) ON CONFLICT (event_id) DO NOTHING",
                eventId, tenantId, eventType) > 0;
    }

    // ------------------------------------------------------------- Handlers

    /**
     * A completed checkout. In {@code payment} mode this grants a one-time pass;
     * in {@code subscription} mode the subscription itself arrives on its own
     * event, so only the customer mapping is recorded here.
     */
    private void handleCheckoutCompleted(String tenantId, JsonNode session) {
        String userId = resolveUserId(tenantId, session);
        String stripeCustomerId = text(session, "customer");
        if (userId == null) {
            log.warn("Checkout completed for tenant {} with no resolvable member "
                    + "(no client_reference_id, metadata.userId, or known customer)", tenantId);
            return;
        }
        if (stripeCustomerId != null) {
            customerRepository.upsert(tenantId, userId, stripeCustomerId,
                    text(session.path("customer_details"), "email"));
        }

        if (!"payment".equals(text(session, "mode"))) {
            return; // subscription mode: the subscription event does the rest
        }

        String planCode = text(session.path("metadata"), "planCode");
        Optional<BillingPlan> plan = planCode == null
                ? Optional.empty()
                : planRepository.findByCode(tenantId, planCode);
        if (plan.isEmpty()) {
            log.warn("Checkout completed for tenant {} member {} with unknown planCode '{}' — "
                    + "no pass granted", tenantId, userId, planCode);
            return;
        }

        Instant startsAt = Instant.now();
        Integer durationDays = plan.get().passDurationDays();
        Instant expiresAt = durationDays == null
                ? null
                : startsAt.plus(Duration.ofDays(durationDays));

        boolean granted = passRepository.grant(tenantId, userId, plan.get().id(),
                text(session, "id"), text(session, "payment_intent"), startsAt, expiresAt);
        if (granted) {
            log.info("Granted billing pass to member {} of tenant {} (plan {}, expires {})",
                    userId, tenantId, plan.get().code(), expiresAt);
            publishEntitlementChanged(tenantId, userId, plan.get().code(), "ACTIVE", "PASS_GRANTED");
        }
    }

    private void handleSubscriptionUpsert(String tenantId, JsonNode subscription) {
        String stripeSubscriptionId = text(subscription, "id");
        String stripeCustomerId = text(subscription, "customer");
        String userId = resolveUserId(tenantId, subscription);
        if (stripeSubscriptionId == null || userId == null) {
            log.warn("Subscription event for tenant {} with no resolvable member or id", tenantId);
            return;
        }

        String priceId = text(subscription.path("items").path("data").path(0).path("price"), "id");
        String planId = planRepository.findByStripePriceId(tenantId, priceId)
                .map(BillingPlan::id)
                .orElse(null);
        if (planId == null) {
            // Mirror the subscription anyway: its status still matters, and the
            // plan can be mapped later without replaying the event.
            log.warn("Subscription {} for tenant {} references unmapped price {}",
                    stripeSubscriptionId, tenantId, priceId);
        }

        String status = text(subscription, "status");
        subscriptionRepository.upsert(tenantId, userId, planId, stripeSubscriptionId,
                stripeCustomerId, status,
                currentPeriodEnd(subscription),
                subscription.path("cancel_at_period_end").asBoolean(false),
                epochSeconds(subscription, "canceled_at"));

        publishEntitlementChanged(tenantId, userId,
                planId == null ? null : planRepository.findById(tenantId, planId)
                        .map(BillingPlan::code).orElse(null),
                status, "SUBSCRIPTION_CHANGED");
        bridgeToFlows(tenantId, "customer.subscription.changed", subscription);
    }

    private void handleSubscriptionDeleted(String tenantId, JsonNode subscription) {
        String stripeSubscriptionId = text(subscription, "id");
        if (stripeSubscriptionId == null) {
            return;
        }
        String status = text(subscription, "status");
        subscriptionRepository.markCanceled(tenantId, stripeSubscriptionId,
                status == null ? "canceled" : status,
                epochSeconds(subscription, "canceled_at"));

        // The member drops to the DEFAULT plan on the next resolve.
        String userId = resolveUserId(tenantId, subscription);
        if (userId != null) {
            publishEntitlementChanged(tenantId, userId, null, "canceled", "SUBSCRIPTION_CANCELED");
        }
        bridgeToFlows(tenantId, "customer.subscription.deleted", subscription);
    }

    // ------------------------------------------------------------- Helpers

    /**
     * Resolves the platform member behind an event: the checkout's
     * {@code client_reference_id}, else {@code metadata.userId} (which the
     * platform stamps on both the session and the subscription), else the stored
     * customer mapping.
     */
    private String resolveUserId(String tenantId, JsonNode object) {
        String userId = text(object, "client_reference_id");
        if (userId != null) {
            return userId;
        }
        userId = text(object.path("metadata"), "userId");
        if (userId != null) {
            return userId;
        }
        String stripeCustomerId = text(object, "customer");
        return customerRepository.findByStripeCustomerId(tenantId, stripeCustomerId)
                .map(c -> c.userId())
                .orElse(null);
    }

    private void publishEntitlementChanged(String tenantId, String userId, String planCode,
                                           String status, String reason) {
        // Same-pod read-after-write, in addition to (never instead of) the
        // broadcast every other pod consumes.
        entitlementService.invalidate(tenantId, userId);

        BillingEntitlementChangedPayload payload =
                new BillingEntitlementChangedPayload(userId, planCode, status, reason);
        PlatformEvent<BillingEntitlementChangedPayload> event =
                EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        eventPublisher.publish(SUBJECT_PREFIX + tenantId + "." + userId, event);
    }

    /**
     * Republishes onto the flow-trigger namespace so tenants can build welcome,
     * dunning-nudge, and win-back automations as configuration. Carries ids and
     * state only — never card data.
     */
    private void bridgeToFlows(String tenantId, String eventType, JsonNode object) {
        BillingEntitlementChangedPayload payload = new BillingEntitlementChangedPayload(
                resolveUserId(tenantId, object), null, text(object, "status"), eventType);
        PlatformEvent<BillingEntitlementChangedPayload> event =
                EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        eventPublisher.publish(TRIGGER_SUBJECT_PREFIX + tenantId + "." + TRIGGER_TOPIC, event);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String s = value.stringValue();
        return s.isBlank() ? null : s;
    }

    /**
     * The subscription's period end, from wherever the event renders it.
     *
     * <p>Stripe moved {@code current_period_end} off the subscription and onto
     * each subscription item in 2025-03-31.basil. Webhook payloads are rendered
     * with the <b>account's</b> API version (or the endpoint's), not the version
     * this client pins for its own calls, so both shapes reach us and reading only
     * the old one silently left the column null — which is what the member-facing
     * "renews on" and "cancels on" lines read.
     */
    private static Instant currentPeriodEnd(JsonNode subscription) {
        Instant topLevel = epochSeconds(subscription, "current_period_end");
        if (topLevel != null) {
            return topLevel;
        }
        // Items can carry different periods; the subscription's own period end is
        // the latest of them.
        Instant latest = null;
        for (JsonNode item : subscription.path("items").path("data")) {
            Instant itemEnd = epochSeconds(item, "current_period_end");
            if (itemEnd != null && (latest == null || itemEnd.isAfter(latest))) {
                latest = itemEnd;
            }
        }
        return latest;
    }

    private static Instant epochSeconds(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        long seconds = value.asLong();
        return seconds <= 0 ? null : Instant.ofEpochSecond(seconds);
    }
}
