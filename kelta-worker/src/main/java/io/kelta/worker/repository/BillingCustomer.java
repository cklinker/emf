package io.kelta.worker.repository;

/**
 * Maps a portal member to their payment-processor customer, read from
 * {@code billing_customer}. One customer per member per tenant.
 */
public record BillingCustomer(
        String id,
        String tenantId,
        String userId,
        String stripeCustomerId,
        String email) {
}
