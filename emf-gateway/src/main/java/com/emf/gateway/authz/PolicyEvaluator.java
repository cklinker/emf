package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import org.springframework.stereotype.Component;

/**
 * Evaluates authorization policies against authenticated principals.
 * 
 * This service determines whether a GatewayPrincipal satisfies the requirements
 * of a given authorization policy. It uses OR logic for role matching: the principal
 * needs ANY of the required roles to satisfy the policy.
 */
@Component
public class PolicyEvaluator {
    
    /**
     * Evaluates whether a principal satisfies a route policy.
     * 
     * Uses OR logic: the principal needs ANY of the required roles.
     * If the policy has no roles, it is considered satisfied.
     *
     * @param policy the route policy to evaluate
     * @param principal the authenticated principal
     * @return true if the principal satisfies the policy, false otherwise
     */
    public boolean evaluate(RoutePolicy policy, GatewayPrincipal principal) {
        if (policy == null || principal == null) {
            return false;
        }
        
        // If policy has no roles, it's satisfied
        if (policy.getRoles() == null || policy.getRoles().isEmpty()) {
            return true;
        }
        
        // Check if principal has ANY of the required roles (OR logic)
        for (String requiredRole : policy.getRoles()) {
            if (hasRole(principal, requiredRole)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Evaluates whether a principal satisfies a field policy.
     * 
     * Uses OR logic: the principal needs ANY of the required roles.
     * If the policy has no roles, it is considered satisfied.
     *
     * @param policy the field policy to evaluate
     * @param principal the authenticated principal
     * @return true if the principal satisfies the policy, false otherwise
     */
    public boolean evaluate(FieldPolicy policy, GatewayPrincipal principal) {
        if (policy == null || principal == null) {
            return false;
        }
        
        // If policy has no roles, it's satisfied
        if (policy.getRoles() == null || policy.getRoles().isEmpty()) {
            return true;
        }
        
        // Check if principal has ANY of the required roles (OR logic)
        for (String requiredRole : policy.getRoles()) {
            if (hasRole(principal, requiredRole)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a principal has a specific role.
     * 
     * Role matching is case-sensitive.
     *
     * @param principal the authenticated principal
     * @param role the role to check
     * @return true if the principal has the role, false otherwise
     */
    public boolean hasRole(GatewayPrincipal principal, String role) {
        if (principal == null || role == null) {
            return false;
        }
        
        return principal.getRoles().contains(role);
    }
}
