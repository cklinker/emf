package com.emf.gateway.auth;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an authenticated user in the gateway.
 * Contains user information extracted from JWT token claims.
 */
public class GatewayPrincipal {
    
    private final String username;
    private final List<String> roles;
    private final Map<String, Object> claims;
    
    /**
     * Creates a new GatewayPrincipal.
     *
     * @param username the username of the authenticated user
     * @param roles the roles assigned to the user
     * @param claims all JWT claims for the user
     */
    public GatewayPrincipal(String username, List<String> roles, Map<String, Object> claims) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.roles = roles != null ? List.copyOf(roles) : Collections.emptyList();
        this.claims = claims != null ? Map.copyOf(claims) : Collections.emptyMap();
    }
    
    /**
     * Gets the username of the authenticated user.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Gets the roles assigned to the user.
     *
     * @return an immutable list of roles
     */
    public List<String> getRoles() {
        return roles;
    }
    
    /**
     * Gets all JWT claims for the user.
     *
     * @return an immutable map of claims
     */
    public Map<String, Object> getClaims() {
        return claims;
    }
    
    /**
     * Checks if the user has a specific role.
     *
     * @param role the role to check
     * @return true if the user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GatewayPrincipal that = (GatewayPrincipal) o;
        return Objects.equals(username, that.username) &&
               Objects.equals(roles, that.roles) &&
               Objects.equals(claims, that.claims);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(username, roles, claims);
    }
    
    @Override
    public String toString() {
        return "GatewayPrincipal{" +
               "username='" + username + '\'' +
               ", roles=" + roles +
               ", claims=" + claims.keySet() +
               '}';
    }
}
