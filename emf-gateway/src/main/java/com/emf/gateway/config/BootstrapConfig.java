package com.emf.gateway.config;

import java.util.List;
import java.util.Map;

/**
 * Data model representing the complete bootstrap configuration from the worker service.
 *
 * This configuration is fetched when the gateway starts and contains all necessary
 * information to set up routes and per-tenant governor limits.
 */
public class BootstrapConfig {

    private List<CollectionConfig> collections;
    private Map<String, GovernorLimitConfig> governorLimits;

    public BootstrapConfig() {
    }

    public BootstrapConfig(List<CollectionConfig> collections) {
        this.collections = collections;
    }

    public List<CollectionConfig> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionConfig> collections) {
        this.collections = collections;
    }

    public Map<String, GovernorLimitConfig> getGovernorLimits() {
        return governorLimits;
    }

    public void setGovernorLimits(Map<String, GovernorLimitConfig> governorLimits) {
        this.governorLimits = governorLimits;
    }

    @Override
    public String toString() {
        return "BootstrapConfig{" +
               "collections=" + (collections != null ? collections.size() : 0) + " collections" +
               ", governorLimits=" + (governorLimits != null ? governorLimits.size() : 0) + " tenants" +
               '}';
    }
}
