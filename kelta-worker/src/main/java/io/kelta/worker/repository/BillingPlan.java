package io.kelta.worker.repository;

/**
 * A plan a tenant offers its portal members, read from {@code billing_plan}.
 *
 * <p>{@code entitlements} carries the raw JSON text stored in the JSONB column
 * (e.g. {@code {"maxActiveWatches":10,"channels":["push"]}}). The platform never
 * interprets the keys — {@code EntitlementService} merges and compares the values
 * — so a tenant introduces a new limit without a schema change.
 */
public record BillingPlan(
        String id,
        String tenantId,
        String code,
        String name,
        String kind,
        String stripeProductId,
        String stripePriceId,
        String entitlements,
        Integer passDurationDays,
        boolean active) {

    /** The free/lapsed baseline every member falls back to. */
    public static final String KIND_DEFAULT = "DEFAULT";
    public static final String KIND_SUBSCRIPTION = "SUBSCRIPTION";
    public static final String KIND_ONE_TIME = "ONE_TIME";
}
