package com.emf.gateway.config;

/**
 * Configuration for a field policy assignment.
 * 
 * Associates a policy with a specific field in a collection.
 */
public class FieldPolicyConfig {
    
    private String collectionId;
    private String fieldName;
    private String policyId;
    
    public FieldPolicyConfig() {
    }
    
    public FieldPolicyConfig(String collectionId, String fieldName, String policyId) {
        this.collectionId = collectionId;
        this.fieldName = fieldName;
        this.policyId = policyId;
    }
    
    public String getCollectionId() {
        return collectionId;
    }
    
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public String getPolicyId() {
        return policyId;
    }
    
    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }
    
    @Override
    public String toString() {
        return "FieldPolicyConfig{" +
               "collectionId='" + collectionId + '\'' +
               ", fieldName='" + fieldName + '\'' +
               ", policyId='" + policyId + '\'' +
               '}';
    }
}
