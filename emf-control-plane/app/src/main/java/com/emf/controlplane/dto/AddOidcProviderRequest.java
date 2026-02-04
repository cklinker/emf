package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for adding a new OIDC provider.
 */
public class AddOidcProviderRequest {

    @NotBlank(message = "Provider name is required")
    @Size(min = 1, max = 100, message = "Provider name must be between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Issuer URL is required")
    @Size(max = 500, message = "Issuer URL must not exceed 500 characters")
    @Pattern(regexp = "^https?://.*", message = "Issuer must be a valid URL starting with http:// or https://")
    private String issuer;

    @NotBlank(message = "JWKS URI is required")
    @Size(max = 500, message = "JWKS URI must not exceed 500 characters")
    @Pattern(regexp = "^https?://.*", message = "JWKS URI must be a valid URL starting with http:// or https://")
    private String jwksUri;

    @Size(max = 200, message = "Client ID must not exceed 200 characters")
    private String clientId;

    @Size(max = 200, message = "Audience must not exceed 200 characters")
    private String audience;

    @Size(max = 200, message = "Roles claim path must not exceed 200 characters")
    private String rolesClaim;

    @Size(max = 10000, message = "Roles mapping must not exceed 10000 characters")
    private String rolesMapping;

    @Size(max = 200, message = "Email claim path must not exceed 200 characters")
    private String emailClaim;

    @Size(max = 200, message = "Username claim path must not exceed 200 characters")
    private String usernameClaim;

    @Size(max = 200, message = "Name claim path must not exceed 200 characters")
    private String nameClaim;

    public AddOidcProviderRequest() {
    }

    public AddOidcProviderRequest(String name, String issuer, String jwksUri) {
        this.name = name;
        this.issuer = issuer;
        this.jwksUri = jwksUri;
    }

    public AddOidcProviderRequest(String name, String issuer, String jwksUri, String clientId, String audience) {
        this.name = name;
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.clientId = clientId;
        this.audience = audience;
    }

    public AddOidcProviderRequest(String name, String issuer, String jwksUri, String clientId, String audience,
                                   String rolesClaim, String rolesMapping, String emailClaim,
                                   String usernameClaim, String nameClaim) {
        this.name = name;
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.clientId = clientId;
        this.audience = audience;
        this.rolesClaim = rolesClaim;
        this.rolesMapping = rolesMapping;
        this.emailClaim = emailClaim;
        this.usernameClaim = usernameClaim;
        this.nameClaim = nameClaim;
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
        return "AddOidcProviderRequest{" +
                "name='" + name + '\'' +
                ", issuer='" + issuer + '\'' +
                ", jwksUri='" + jwksUri + '\'' +
                ", clientId='" + clientId + '\'' +
                ", audience='" + audience + '\'' +
                ", rolesClaim='" + rolesClaim + '\'' +
                ", rolesMapping='" + rolesMapping + '\'' +
                ", emailClaim='" + emailClaim + '\'' +
                ", usernameClaim='" + usernameClaim + '\'' +
                ", nameClaim='" + nameClaim + '\'' +
                '}';
    }
}
