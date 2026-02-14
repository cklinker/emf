package com.emf.gateway.config;

import java.util.List;

/**
 * Authorization configuration from the control plane.
 * 
 * Contains roles, policies, and policy assignments for routes and fields.
 */
public class AuthorizationConfig {
    
    private List<RoleConfig> roles;
    private List<PolicyConfig> policies;
    private List<RoutePolicyConfig> routePolicies;
    private List<FieldPolicyConfig> fieldPolicies;
    private List<CollectionAuthzConfig> collectionAuthz;

    public AuthorizationConfig() {
    }

    public AuthorizationConfig(List<RoleConfig> roles, List<PolicyConfig> policies,
                              List<RoutePolicyConfig> routePolicies, List<FieldPolicyConfig> fieldPolicies) {
        this.roles = roles;
        this.policies = policies;
        this.routePolicies = routePolicies;
        this.fieldPolicies = fieldPolicies;
    }
    
    public List<RoleConfig> getRoles() {
        return roles;
    }
    
    public void setRoles(List<RoleConfig> roles) {
        this.roles = roles;
    }
    
    public List<PolicyConfig> getPolicies() {
        return policies;
    }
    
    public void setPolicies(List<PolicyConfig> policies) {
        this.policies = policies;
    }
    
    public List<RoutePolicyConfig> getRoutePolicies() {
        return routePolicies;
    }
    
    public void setRoutePolicies(List<RoutePolicyConfig> routePolicies) {
        this.routePolicies = routePolicies;
    }
    
    public List<FieldPolicyConfig> getFieldPolicies() {
        return fieldPolicies;
    }
    
    public void setFieldPolicies(List<FieldPolicyConfig> fieldPolicies) {
        this.fieldPolicies = fieldPolicies;
    }

    public List<CollectionAuthzConfig> getCollectionAuthz() {
        return collectionAuthz;
    }

    public void setCollectionAuthz(List<CollectionAuthzConfig> collectionAuthz) {
        this.collectionAuthz = collectionAuthz;
    }

    @Override
    public String toString() {
        return "AuthorizationConfig{" +
               "roles=" + (roles != null ? roles.size() : 0) + " roles" +
               ", policies=" + (policies != null ? policies.size() : 0) + " policies" +
               ", routePolicies=" + (routePolicies != null ? routePolicies.size() : 0) + " route policies" +
               ", fieldPolicies=" + (fieldPolicies != null ? fieldPolicies.size() : 0) + " field policies" +
               ", collectionAuthz=" + (collectionAuthz != null ? collectionAuthz.size() : 0) + " collection authz" +
               '}';
    }
}
