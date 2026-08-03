package io.kelta.runtime.event;

/**
 * Payload for member entitlement changes (portal billing). Published to
 * {@code kelta.billing.entitlement.changed.<tenantId>.<userId>} inside a
 * {@link PlatformEvent} envelope whenever a subscription, pass, or plan change
 * alters what a portal member is entitled to.
 *
 * <p>Carries ids and coarse state ONLY — never card data, processor secrets, or
 * the resolved entitlement map. Every pod evicts its cached entitlements for the
 * member on receipt; the authoritative values are re-resolved from the database.
 *
 * @since 1.0.0
 */
public class BillingEntitlementChangedPayload {

    private String userId;
    private String planCode;
    private String status;
    private String reason;

    public BillingEntitlementChangedPayload() {
    }

    public BillingEntitlementChangedPayload(String userId, String planCode,
                                            String status, String reason) {
        this.userId = userId;
        this.planCode = planCode;
        this.status = status;
        this.reason = reason;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
