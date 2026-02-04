package com.emf.gateway.config;

/**
 * Configuration for a backend service.
 * 
 * Represents a service that can handle requests for one or more collections.
 */
public class ServiceConfig {
    
    private String id;
    private String name;
    private String baseUrl;
    
    public ServiceConfig() {
    }
    
    public ServiceConfig(String id, String name, String baseUrl) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    @Override
    public String toString() {
        return "ServiceConfig{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", baseUrl='" + baseUrl + '\'' +
               '}';
    }
}
