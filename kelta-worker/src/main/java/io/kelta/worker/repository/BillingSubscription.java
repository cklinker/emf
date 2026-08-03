package io.kelta.worker.repository;

import java.time.Instant;
import java.util.Set;

/**
 * Mirrored subscription state, read from {@code billing_subscription}.
 *
 * <p>{@code status} holds the payment processor's own vocabulary verbatim rather
 * than a platform enum: an unrecognized future status then degrades to "not
 * entitled" instead of failing a CHECK and dropping the webhook.
 */
public record BillingSubscription(
        String id,
        String tenantId,
        String userId,
        String planId,
        String stripeSubscriptionId,
        String stripeCustomerId,
        String status,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant canceledAt) {

    /**
     * Statuses that still grant the plan's entitlements. {@code past_due} is
     * included deliberately — the processor is retrying payment, and cutting a
     * member off mid-retry punishes a recoverable card failure.
     */
    public static final Set<String> ENTITLING_STATUSES =
            Set.of("active", "trialing", "past_due");

    /** True when this subscription should confer its plan's entitlements. */
    public boolean isEntitling() {
        return status != null && ENTITLING_STATUSES.contains(status.toLowerCase());
    }
}
