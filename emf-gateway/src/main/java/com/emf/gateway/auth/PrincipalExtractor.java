package com.emf.gateway.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extracts user information from JWT token claims to create a GatewayPrincipal.
 * Handles extraction of username, roles, and all claims from the JWT.
 */
@Component
public class PrincipalExtractor {
    
    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";
    private static final String SUB_CLAIM = "sub";
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORITIES_CLAIM = "authorities";
    
    /**
     * Extracts a GatewayPrincipal from a JWT token.
     * 
     * Username is extracted from "preferred_username" claim, falling back to "sub" if not present.
     * Roles are extracted from "roles" claim, falling back to "authorities" if not present.
     *
     * @param jwt the JWT token to extract information from
     * @return a GatewayPrincipal containing the user information
     * @throws IllegalArgumentException if the JWT is null or missing required claims
     */
    public GatewayPrincipal extractPrincipal(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("JWT cannot be null");
        }
        
        String username = extractUsername(jwt);
        List<String> roles = extractRoles(jwt);
        Map<String, Object> claims = jwt.getClaims();
        
        return new GatewayPrincipal(username, roles, claims);
    }
    
    /**
     * Extracts the username from the JWT.
     * Tries "preferred_username" first, then falls back to "sub".
     *
     * @param jwt the JWT token
     * @return the username
     * @throws IllegalArgumentException if neither claim is present
     */
    private String extractUsername(Jwt jwt) {
        String username = jwt.getClaimAsString(PREFERRED_USERNAME_CLAIM);
        if (username != null && !username.isEmpty()) {
            return username;
        }
        
        username = jwt.getClaimAsString(SUB_CLAIM);
        if (username != null && !username.isEmpty()) {
            return username;
        }
        
        throw new IllegalArgumentException("JWT must contain either 'preferred_username' or 'sub' claim");
    }
    
    /**
     * Extracts roles from the JWT.
     * Tries "roles" claim first, then falls back to "authorities".
     * Returns an empty list if neither claim is present.
     *
     * @param jwt the JWT token
     * @return a list of roles, or an empty list if no roles are found
     */
    private List<String> extractRoles(Jwt jwt) {
        // Try "roles" claim first
        List<String> roles = extractRolesFromClaim(jwt, ROLES_CLAIM);
        if (!roles.isEmpty()) {
            return roles;
        }
        
        // Fall back to "authorities" claim
        roles = extractRolesFromClaim(jwt, AUTHORITIES_CLAIM);
        return roles;
    }
    
    /**
     * Extracts roles from a specific claim in the JWT.
     * Handles both List<String> and comma-separated string formats.
     *
     * @param jwt the JWT token
     * @param claimName the name of the claim to extract roles from
     * @return a list of roles, or an empty list if the claim is not present or invalid
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromClaim(Jwt jwt, String claimName) {
        Object claimValue = jwt.getClaim(claimName);
        
        if (claimValue == null) {
            return Collections.emptyList();
        }
        
        // Handle List<String> format
        if (claimValue instanceof List) {
            try {
                List<?> list = (List<?>) claimValue;
                List<String> roles = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String) {
                        roles.add((String) item);
                    }
                }
                return roles;
            } catch (ClassCastException e) {
                return Collections.emptyList();
            }
        }
        
        // Handle comma-separated string format
        if (claimValue instanceof String) {
            String rolesString = (String) claimValue;
            if (rolesString.isEmpty()) {
                return Collections.emptyList();
            }
            return List.of(rolesString.split(","));
        }
        
        return Collections.emptyList();
    }
}
