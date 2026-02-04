package com.emf.gateway.authz;

import java.util.List;
import java.util.Objects;

/**
 * Authorization configuration for a specific collection.
 * 
 * Contains route policies and field policies for a collection.
 * This is the runtime representation used by authorization filters.
 */
public class AuthzConfig {
    
    private final String collectionId;
    private final List<RoutePolicy> routePolicies;
    private final List<FieldPolicy> fieldPolicies;
    
    public AuthzConfig(String collectionId, List<RoutePolicy> routePolicies, List<FieldPolicy> fieldPolicies) {
        this.collectionId = Objects.requireNonNull(collectionId, "collectionId cannot be null");
        this.routePolicies = Objects.requireNonNull(routePolicies, "routePolicies cannot be null");
        this.fieldPolicies = Objects.requireNonNull(fieldPolicies, "fieldPolicies cannot be null");
    }
    
    /**
     * @return the collection ID this configuration applies to
     */
    public String getCollectionId() {
        return collectionId;
    }
    
    /**
     * @return the list of route policies for this collection
     */
    public List<RoutePolicy> getRoutePolicies() {
        return routePolicies;
    }
    
    /**
     * @return the list of field policies for this collection
     */
    public List<FieldPolicy> getFieldPolicies() {
        return fieldPolicies;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthzConfig that = (AuthzConfig) o;
        return Objects.equals(collectionId, that.collectionId) &&
               Objects.equals(routePolicies, that.routePolicies) &&
               Objects.equals(fieldPolicies, that.fieldPolicies);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(collectionId, routePolicies, fieldPolicies);
    }
    
    @Override
    public String toString() {
        return "AuthzConfig{" +
               "collectionId='" + collectionId + '\'' +
               ", routePolicies=" + routePolicies.size() + " policies" +
               ", fieldPolicies=" + fieldPolicies.size() + " policies" +
               '}';
    }
}
