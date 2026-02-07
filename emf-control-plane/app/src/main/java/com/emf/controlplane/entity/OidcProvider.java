package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Represents an OpenID Connect identity provider configuration.
 * Used for JWT validation and authentication.
 */
@Entity
@Table(name = "oidc_provider")
public class OidcProvider extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "issuer", nullable = false, length = 500)
    private String issuer;

    @Column(name = "jwks_uri", nullable = false, length = 500)
    private String jwksUri;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "client_id", length = 200)
    private String clientId;

    @Column(name = "audience", length = 200)
    private String audience;

    @Column(name = "roles_claim", length = 200)
    private String rolesClaim;

    @Column(name = "roles_mapping", columnDefinition = "TEXT")
    private String rolesMapping;

    @Column(name = "email_claim", length = 200)
    private String emailClaim;

    @Column(name = "username_claim", length = 200)
    private String usernameClaim;

    @Column(name = "name_claim", length = 200)
    private String nameClaim;

    public OidcProvider() {
        super();
    }

    public OidcProvider(String name, String issuer, String jwksUri) {
        super();
        this.name = name;
        this.issuer = issuer;
        this.jwksUri = jwksUri;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
        return emailClaim != null ? emailClaim : "email";
    }

    public void setEmailClaim(String emailClaim) {
        this.emailClaim = emailClaim;
    }

    public String getUsernameClaim() {
        return usernameClaim != null ? usernameClaim : "preferred_username";
    }

    public void setUsernameClaim(String usernameClaim) {
        this.usernameClaim = usernameClaim;
    }

    public String getNameClaim() {
        return nameClaim != null ? nameClaim : "name";
    }

    public void setNameClaim(String nameClaim) {
        this.nameClaim = nameClaim;
    }

    @Override
    public String toString() {
        return "OidcProvider{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", issuer='" + issuer + '\'' +
                ", active=" + active +
                ", rolesClaim='" + rolesClaim + '\'' +
                ", rolesMapping='" + rolesMapping + '\'' +
                ", emailClaim='" + emailClaim + '\'' +
                ", usernameClaim='" + usernameClaim + '\'' +
                ", nameClaim='" + nameClaim + '\'' +
                '}';
    }
}
