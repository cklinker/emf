package com.emf.runtime.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Payload for authorization changed events.
 * Contains the full authorization configuration for a collection.
 * 
 * This is a shared event payload used across all EMF services.
 */
public class AuthzChangedPayload {

    private String collectionId;
    private String collectionName;
    private List<RoutePolicyPayload> routePolicies;
    private List<FieldPolicyPayload> fieldPolicies;
    private Instant timestamp;

    /**
     * Default constructor for deserialization.
     */
    public AuthzChangedPayload() {
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public List<RoutePolicyPayload> getRoutePolicies() {
        return routePolicies;
    }

    public void setRoutePolicies(List<RoutePolicyPayload> routePolicies) {
        this.routePolicies = routePolicies;
    }

    public List<FieldPolicyPayload> getFieldPolicies() {
        return fieldPolicies;
    }

    public void setFieldPolicies(List<FieldPolicyPayload> fieldPolicies) {
        this.fieldPolicies = fieldPolicies;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthzChangedPayload that = (AuthzChangedPayload) o;
        return Objects.equals(collectionId, that.collectionId) &&
                Objects.equals(collectionName, that.collectionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionId, collectionName);
    }

    @Override
    public String toString() {
        return "AuthzChangedPayload{" +
                "collectionId='" + collectionId + '\'' +
                ", collectionName='" + collectionName + '\'' +
                ", routePolicies=" + (routePolicies != null ? routePolicies.size() : 0) +
                ", fieldPolicies=" + (fieldPolicies != null ? fieldPolicies.size() : 0) +
                '}';
    }

    /**
     * Nested class for route policy data in the payload.
     */
    public static class RoutePolicyPayload {
        private String id;
        private String operation;
        private String policyId;
        private String policyName;
        private String policyRules;

        public RoutePolicyPayload() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getPolicyId() {
            return policyId;
        }

        public void setPolicyId(String policyId) {
            this.policyId = policyId;
        }

        public String getPolicyName() {
            return policyName;
        }

        public void setPolicyName(String policyName) {
            this.policyName = policyName;
        }

        public String getPolicyRules() {
            return policyRules;
        }

        public void setPolicyRules(String policyRules) {
            this.policyRules = policyRules;
        }
    }

    /**
     * Nested class for field policy data in the payload.
     */
    public static class FieldPolicyPayload {
        private String id;
        private String fieldId;
        private String fieldName;
        private String operation;
        private String policyId;
        private String policyName;
        private String policyRules;

        public FieldPolicyPayload() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFieldId() {
            return fieldId;
        }

        public void setFieldId(String fieldId) {
            this.fieldId = fieldId;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getPolicyId() {
            return policyId;
        }

        public void setPolicyId(String policyId) {
            this.policyId = policyId;
        }

        public String getPolicyName() {
            return policyName;
        }

        public void setPolicyName(String policyName) {
            this.policyName = policyName;
        }

        public String getPolicyRules() {
            return policyRules;
        }

        public void setPolicyRules(String policyRules) {
            this.policyRules = policyRules;
        }
    }
}
