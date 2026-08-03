package io.kelta.worker.repository;

/**
 * Quota enforcement expressed as configuration, read from
 * {@code billing_entitlement_rule}: "records in {@code collectionName} are capped
 * by entitlement key {@code limitKey}".
 *
 * <p>{@code countFilter} is raw JSON text naming extra equality predicates to
 * apply when counting the member's existing rows (e.g. only count {@code ACTIVE}
 * ones). Its field names are validated against the collection's field registry
 * before use and its values are always bound as parameters.
 */
public record BillingEntitlementRule(
        String id,
        String tenantId,
        String collectionName,
        String limitKey,
        String countFilter,
        String appliesTo,
        String message,
        boolean active) {

    /** Rule applies only to PORTAL members; internal staff are unconstrained. */
    public static final String APPLIES_TO_PORTAL = "PORTAL";
    public static final String APPLIES_TO_ALL = "ALL";
}
