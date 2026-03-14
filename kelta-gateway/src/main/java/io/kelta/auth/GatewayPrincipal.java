package io.kelta.gateway.auth;

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
    private final List<String> groups;
    private final Map<String, Object> claims;
    private String profileId;
    private String profileName;
    private String tenantId;

    /**
     * Creates a new GatewayPrincipal.
     *
     * @param username the username of the authenticated user
     * @param groups the OIDC groups extracted from the JWT token
     * @param claims all JWT claims for the user
     */
    public GatewayPrincipal(String username, List<String> groups, Map<String, Object> claims) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.groups = groups != null ? List.copyOf(groups) : Collections.emptyList();
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
     * Gets the OIDC groups extracted from the JWT token.
     *
     * @return an immutable list of groups
     */
    public List<String> getGroups() {
        return groups;
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
     * Checks if the user belongs to a specific OIDC group.
     *
     * @param group the group to check
     * @return true if the user belongs to the group, false otherwise
     */
    public boolean hasGroup(String group) {
        return groups.contains(group);
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GatewayPrincipal that = (GatewayPrincipal) o;
        return Objects.equals(username, that.username) &&
               Objects.equals(groups, that.groups) &&
               Objects.equals(claims, that.claims);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, groups, claims);
    }

    @Override
    public String toString() {
        return "GatewayPrincipal{" +
               "username='" + username + '\'' +
               ", groups=" + groups +
               ", profileId='" + profileId + '\'' +
               ", profileName='" + profileName + '\'' +
               ", tenantId='" + tenantId + '\'' +
               ", claims=" + claims.keySet() +
               '}';
    }
}
