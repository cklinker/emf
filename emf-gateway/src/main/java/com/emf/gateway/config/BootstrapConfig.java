package com.emf.gateway.config;

import java.util.List;

/**
 * Data model representing the complete bootstrap configuration from the control plane.
 *
 * This configuration is fetched when the gateway starts and contains all necessary
 * information to set up routes.
 */
public class BootstrapConfig {

    private List<CollectionConfig> collections;

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

    @Override
    public String toString() {
        return "BootstrapConfig{" +
               "collections=" + (collections != null ? collections.size() : 0) + " collections" +
               '}';
    }
}
