package com.emf.controlplane.dto;

import com.emf.controlplane.entity.OidcProvider;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for OIDC Provider API responses.
 * Provides a clean API representation of an OidcProvider entity.
 */
public class OidcProviderDto {

    private String id;
    private String name;
    private String issuer;
    private String jwksUri;
    private String clientId;
    private String audience;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private String rolesClaim;
    private String rolesMapping;
    private String emailClaim;
    private String usernameClaim;
    private String nameClaim;

    public OidcProviderDto() {
    }

    public OidcProviderDto(String id, String name, String issuer, String jwksUri, 
                           String clientId, String audience, boolean active,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.clientId = clientId;
        this.audience = audience;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates an OidcProviderDto from an OidcProvider entity.
     * 
     * @param provider The OIDC provider entity to convert
     * @return A new OidcProviderDto with data from the entity
     */
    public static OidcProviderDto fromEntity(OidcProvider provider) {
        if (provider == null) {
            return null;
        }
        OidcProviderDto dto = new OidcProviderDto(
                provider.getId(),
                provider.getName(),
                provider.getIssuer(),
                provider.getJwksUri(),
                provider.getClientId(),
                provider.getAudience(),
                provider.isActive(),
                provider.getCreatedAt(),
                provider.getUpdatedAt()
        );
        dto.setRolesClaim(provider.getRolesClaim());
        dto.setRolesMapping(provider.getRolesMapping());
        dto.setEmailClaim(provider.getEmailClaim());
        dto.setUsernameClaim(provider.getUsernameClaim());
        dto.setNameClaim(provider.getNameClaim());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public String getRolesMapping() {
        return rolesMapping;
    }

    public void setRolesMapping(String rolesMapping) {
        this.rolesMapping = rolesMapping;
    }

    public String getEmailClaim() {
        return emailClaim;
    }

    public void setEmailClaim(String emailClaim) {
        this.emailClaim = emailClaim;
    }

    public String getUsernameClaim() {
        return usernameClaim;
    }

    public void setUsernameClaim(String usernameClaim) {
        this.usernameClaim = usernameClaim;
    }

    public String getNameClaim() {
        return nameClaim;
    }

    public void setNameClaim(String nameClaim) {
        this.nameClaim = nameClaim;
    }

    @Override
    public String toString() {
        return "OidcProviderDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", issuer='" + issuer + '\'' +
                ", jwksUri='" + jwksUri + '\'' +
                ", clientId='" + clientId + '\'' +
                ", audience='" + audience + '\'' +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", rolesClaim='" + rolesClaim + '\'' +
                ", rolesMapping='" + rolesMapping + '\'' +
                ", emailClaim='" + emailClaim + '\'' +
                ", usernameClaim='" + usernameClaim + '\'' +
                ", nameClaim='" + nameClaim + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OidcProviderDto that = (OidcProviderDto) o;
        return active == that.active &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(issuer, that.issuer) &&
                Objects.equals(jwksUri, that.jwksUri) &&
                Objects.equals(clientId, that.clientId) &&
                Objects.equals(audience, that.audience) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt) &&
                Objects.equals(rolesClaim, that.rolesClaim) &&
                Objects.equals(rolesMapping, that.rolesMapping) &&
                Objects.equals(emailClaim, that.emailClaim) &&
                Objects.equals(usernameClaim, that.usernameClaim) &&
                Objects.equals(nameClaim, that.nameClaim);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, issuer, jwksUri, clientId, audience, active, createdAt, updatedAt,
                rolesClaim, rolesMapping, emailClaim, usernameClaim, nameClaim);
    }
}
