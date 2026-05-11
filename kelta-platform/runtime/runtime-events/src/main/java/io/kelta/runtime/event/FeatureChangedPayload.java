package io.kelta.runtime.event;

import java.util.Objects;

/**
 * Payload for {@code kelta.config.feature.changed.<tenantId>} events.
 *
 * <p>Published when system feature toggles, governor limits, or other
 * tenant-scoped settings change so all worker and gateway pods can evict
 * derived caches (tenant limits, feature gates) instead of waiting for TTL
 * expiry.
 *
 * <p>The {@code scope} field describes the area that changed (e.g.,
 * {@code "limits"}, {@code "features"}) for diagnostic logging. Listeners
 * generally evict all tenant-scoped caches regardless of scope.
 */
public class FeatureChangedPayload {

    private String tenantId;
    private String scope;
    private ChangeType changeType;

    public FeatureChangedPayload() {
    }

    public FeatureChangedPayload(String tenantId, String scope, ChangeType changeType) {
        this.tenantId = tenantId;
        this.scope = scope;
        this.changeType = changeType;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeatureChangedPayload that)) return false;
        return Objects.equals(tenantId, that.tenantId)
            && Objects.equals(scope, that.scope)
            && changeType == that.changeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, scope, changeType);
    }

    @Override
    public String toString() {
        return "FeatureChangedPayload{tenantId=" + tenantId + ", scope=" + scope
            + ", changeType=" + changeType + '}';
    }
}
