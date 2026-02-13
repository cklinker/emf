package com.emf.gateway.config;

import java.util.List;

/**
 * Data model representing the complete bootstrap configuration from the control plane.
 *
 * This configuration is fetched when the gateway starts and contains all necessary
 * information to set up routes and authorization policies.
 */
public class BootstrapConfig {

    private List<CollectionConfig> collections;
    private AuthorizationConfig authorization;

    public BootstrapConfig() {
    }

    public BootstrapConfig(List<CollectionConfig> collections,
                          AuthorizationConfig authorization) {
        this.collections = collections;
        this.authorization = authorization;
    }

    public List<CollectionConfig> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionConfig> collections) {
        this.collections = collections;
    }

    public AuthorizationConfig getAuthorization() {
        return authorization;
    }

    public void setAuthorization(AuthorizationConfig authorization) {
        this.authorization = authorization;
    }

    @Override
    public String toString() {
        return "BootstrapConfig{" +
               "collections=" + (collections != null ? collections.size() : 0) + " collections" +
               ", authorization=" + authorization +
               '}';
    }
}
