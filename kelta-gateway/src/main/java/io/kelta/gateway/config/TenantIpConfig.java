package io.kelta.gateway.config;

import java.util.Collections;
import java.util.List;

/**
 * Per-tenant IP allowlist configuration received from the worker bootstrap
 * (and the {@code /internal/ip-allowlists} refresh endpoint).
 *
 * <p>Used by {@link io.kelta.gateway.filter.TenantIpAllowlistFilter} to decide
 * whether a non-admin request's source IP is permitted. When {@code enabled} is
 * false or {@code cidrs} is empty, the tenant has no network restriction.
 */
public class TenantIpConfig {

    private boolean enabled;
    private List<String> cidrs;

    public TenantIpConfig() {
        this.cidrs = Collections.emptyList();
    }

    public TenantIpConfig(boolean enabled, List<String> cidrs) {
        this.enabled = enabled;
        this.cidrs = cidrs != null ? List.copyOf(cidrs) : Collections.emptyList();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getCidrs() {
        return cidrs;
    }

    public void setCidrs(List<String> cidrs) {
        this.cidrs = cidrs != null ? List.copyOf(cidrs) : Collections.emptyList();
    }

    /**
     * Returns true when this tenant restricts access (enabled with at least one CIDR).
     */
    public boolean isRestricted() {
        return enabled && cidrs != null && !cidrs.isEmpty();
    }

    @Override
    public String toString() {
        return "TenantIpConfig{enabled=" + enabled
                + ", cidrs=" + (cidrs != null ? cidrs.size() : 0) + "}";
    }
}
