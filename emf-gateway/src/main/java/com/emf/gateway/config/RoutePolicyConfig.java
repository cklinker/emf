package com.emf.gateway.config;

/**
 * Configuration for a route policy assignment.
 * 
 * Associates a policy with a specific collection and HTTP method.
 */
public class RoutePolicyConfig {
    
    private String collectionId;
    private String method;
    private String policyId;
    
    public RoutePolicyConfig() {
    }
    
    public RoutePolicyConfig(String collectionId, String method, String policyId) {
        this.collectionId = collectionId;
        this.method = method;
        this.policyId = policyId;
    }
    
    public String getCollectionId() {
        return collectionId;
    }
    
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getPolicyId() {
        return policyId;
    }
    
    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }
    
    @Override
    public String toString() {
        return "RoutePolicyConfig{" +
               "collectionId='" + collectionId + '\'' +
               ", method='" + method + '\'' +
               ", policyId='" + policyId + '\'' +
               '}';
    }
}
