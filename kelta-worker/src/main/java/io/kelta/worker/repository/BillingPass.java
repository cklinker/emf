package io.kelta.worker.repository;

import java.time.Instant;

/**
 * A one-time pass — a bounded window of elevated entitlements — read from
 * {@code billing_pass}.
 *
 * <p>Expiry is evaluated at read time via {@link #isLive(Instant)} regardless of
 * the stored {@code status}: the expiry sweep only tidies the row, it is never
 * the thing that decides whether a member is still entitled.
 */
public record BillingPass(
        String id,
        String tenantId,
        String userId,
        String planId,
        String stripeCheckoutSessionId,
        String status,
        Instant startsAt,
        Instant expiresAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** True when the pass is ACTIVE, has started, and has not expired at {@code now}. */
    public boolean isLive(Instant now) {
        if (!STATUS_ACTIVE.equals(status)) {
            return false;
        }
        if (startsAt != null && startsAt.isAfter(now)) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
