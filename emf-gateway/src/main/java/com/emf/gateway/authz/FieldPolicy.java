package com.emf.gateway.authz;

import java.util.List;
import java.util.Objects;

/**
 * Field authorization policy for a specific field in a collection.
 * 
 * Contains the policy ID and required roles for viewing a field.
 * This is the runtime representation used by field filtering.
 */
public class FieldPolicy {
    
    private final String fieldName;
    private final String policyId;
    private final List<String> roles;
    
    public FieldPolicy(String fieldName, String policyId, List<String> roles) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.policyId = Objects.requireNonNull(policyId, "policyId cannot be null");
        this.roles = Objects.requireNonNull(roles, "roles cannot be null");
    }
    
    /**
     * @return the field name this policy applies to
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * @return the policy ID from the control plane
     */
    public String getPolicyId() {
        return policyId;
    }
    
    /**
     * @return the list of roles required to view this field
     */
    public List<String> getRoles() {
        return roles;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldPolicy that = (FieldPolicy) o;
        return Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(policyId, that.policyId) &&
               Objects.equals(roles, that.roles);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fieldName, policyId, roles);
    }
    
    @Override
    public String toString() {
        return "FieldPolicy{" +
               "fieldName='" + fieldName + '\'' +
               ", policyId='" + policyId + '\'' +
               ", roles=" + roles +
               '}';
    }
}
