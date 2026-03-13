package io.kelta.gateway.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extracts user information from JWT token claims to create a GatewayPrincipal.
 * Handles extraction of username, groups, and all claims from the JWT.
 */
@Component
public class PrincipalExtractor {
    
    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";
    private static final String SUB_CLAIM = "sub";
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String GROUPS_CLAIM = "groups";
    
    /**
     * Extracts a GatewayPrincipal from a JWT token.
     * 
     * Username is extracted from "preferred_username" claim, falling back to "sub" if not present.
     * Groups are extracted from "roles" claim, falling back to "authorities" then "groups".
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
        List<String> groups = extractGroups(jwt);
        Map<String, Object> claims = jwt.getClaims();

        return new GatewayPrincipal(username, groups, claims);
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
     * Extracts groups from the JWT.
     * Tries "roles" claim first, then "authorities", then "groups" (Authentik).
     * Returns an empty list if no group claims are present.
     *
     * @param jwt the JWT token
     * @return a list of groups, or an empty list if no groups are found
     */
    private List<String> extractGroups(Jwt jwt) {
        // Try "roles" claim first
        List<String> groups = extractGroupsFromClaim(jwt, ROLES_CLAIM);
        if (!groups.isEmpty()) {
            return groups;
        }

        // Fall back to "authorities" claim
        groups = extractGroupsFromClaim(jwt, AUTHORITIES_CLAIM);
        if (!groups.isEmpty()) {
            return groups;
        }

        // Fall back to "groups" claim (used by Authentik and other providers)
        return extractGroupsFromClaim(jwt, GROUPS_CLAIM);
    }

    /**
     * Extracts groups from a specific claim in the JWT.
     * Handles both List&lt;String&gt; and comma-separated string formats.
     *
     * @param jwt the JWT token
     * @param claimName the name of the claim to extract groups from
     * @return a list of groups, or an empty list if the claim is not present or invalid
     */
    @SuppressWarnings("unchecked")
    private List<String> extractGroupsFromClaim(Jwt jwt, String claimName) {
        Object claimValue = jwt.getClaim(claimName);

        if (claimValue == null) {
            return Collections.emptyList();
        }

        // Handle List<String> format
        if (claimValue instanceof List) {
            try {
                List<?> list = (List<?>) claimValue;
                List<String> groups = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String) {
                        groups.add((String) item);
                    }
                }
                return groups;
            } catch (ClassCastException e) {
                return Collections.emptyList();
            }
        }

        // Handle comma-separated string format
        if (claimValue instanceof String) {
            String groupsString = (String) claimValue;
            if (groupsString.isEmpty()) {
                return Collections.emptyList();
            }
            return List.of(groupsString.split(","));
        }

        return Collections.emptyList();
    }
}
