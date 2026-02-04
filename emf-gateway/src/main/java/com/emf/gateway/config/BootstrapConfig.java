package com.emf.gateway.config;

import java.util.List;

/**
 * Data model representing the complete bootstrap configuration from the control plane.
 * 
 * This configuration is fetched when the gateway starts and contains all necessary
 * information to set up routes, services, and authorization policies.
 */
public class BootstrapConfig {
    
    private List<ServiceConfig> services;
    private List<CollectionConfig> collections;
    private AuthorizationConfig authorization;
    
    public BootstrapConfig() {
    }
    
    public BootstrapConfig(List<ServiceConfig> services, List<CollectionConfig> collections, 
                          AuthorizationConfig authorization) {
        this.services = services;
        this.collections = collections;
        this.authorization = authorization;
    }
    
    public List<ServiceConfig> getServices() {
        return services;
    }
    
    public void setServices(List<ServiceConfig> services) {
        this.services = services;
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
               "services=" + (services != null ? services.size() : 0) + " services" +
               ", collections=" + (collections != null ? collections.size() : 0) + " collections" +
               ", authorization=" + authorization +
               '}';
    }
}
