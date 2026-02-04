package com.emf.gateway.authz;

import java.util.List;
import java.util.Objects;

/**
 * Route authorization policy for a specific HTTP method.
 * 
 * Contains the policy ID and required roles for accessing a route with a specific HTTP method.
 * This is the runtime representation used by authorization filters.
 */
public class RoutePolicy {
    
    private final String method;
    private final String policyId;
    private final List<String> roles;
    
    public RoutePolicy(String method, String policyId, List<String> roles) {
        this.method = Objects.requireNonNull(method, "method cannot be null");
        this.policyId = Objects.requireNonNull(policyId, "policyId cannot be null");
        this.roles = Objects.requireNonNull(roles, "roles cannot be null");
    }
    
    /**
     * @return the HTTP method this policy applies to (GET, POST, PUT, DELETE, etc.)
     */
    public String getMethod() {
        return method;
    }
    
    /**
     * @return the policy ID from the control plane
     */
    public String getPolicyId() {
        return policyId;
    }
    
    /**
     * @return the list of roles required to satisfy this policy
     */
    public List<String> getRoles() {
        return roles;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutePolicy that = (RoutePolicy) o;
        return Objects.equals(method, that.method) &&
               Objects.equals(policyId, that.policyId) &&
               Objects.equals(roles, that.roles);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(method, policyId, roles);
    }
    
    @Override
    public String toString() {
        return "RoutePolicy{" +
               "method='" + method + '\'' +
               ", policyId='" + policyId + '\'' +
               ", roles=" + roles +
               '}';
    }
}
