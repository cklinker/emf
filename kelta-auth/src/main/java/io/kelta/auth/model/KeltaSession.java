package io.kelta.auth.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class KeltaSession implements Serializable {

    private String email;
    private String tenantId;
    private String tenantSlug;
    private String profileId;
    private String profileName;
    private String displayName;
    private List<String> groups;
    private String authSource; // "external" or "internal"
    private String authMethod; // "internal", "sso:okta", "sso:entra", etc.
    private String idpSessionId; // External IdP session ID for back-channel logout
    private Instant createdAt;

    public KeltaSession() {}

    public KeltaSession(String email, String tenantId, String tenantSlug, String profileId,
                        String profileName, String displayName, List<String> groups,
                        String authSource, Instant createdAt) {
        this.email = email;
        this.tenantId = tenantId;
        this.tenantSlug = tenantSlug;
        this.profileId = profileId;
        this.profileName = profileName;
        this.displayName = displayName;
        this.groups = groups;
        this.authSource = authSource;
        this.createdAt = createdAt;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTenantSlug() { return tenantSlug; }
    public void setTenantSlug(String tenantSlug) { this.tenantSlug = tenantSlug; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }

    public String getAuthSource() { return authSource; }
    public void setAuthSource(String authSource) { this.authSource = authSource; }

    public String getAuthMethod() { return authMethod; }
    public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }

    public String getIdpSessionId() { return idpSessionId; }
    public void setIdpSessionId(String idpSessionId) { this.idpSessionId = idpSessionId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String email;
        private String tenantId;
        private String tenantSlug;
        private String profileId;
        private String profileName;
        private String displayName;
        private List<String> groups;
        private String authSource;
        private String authMethod;
        private String idpSessionId;
        private Instant createdAt;

        public Builder email(String email) { this.email = email; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder tenantSlug(String tenantSlug) { this.tenantSlug = tenantSlug; return this; }
        public Builder profileId(String profileId) { this.profileId = profileId; return this; }
        public Builder profileName(String profileName) { this.profileName = profileName; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder groups(List<String> groups) { this.groups = groups; return this; }
        public Builder authSource(String authSource) { this.authSource = authSource; return this; }
        public Builder authMethod(String authMethod) { this.authMethod = authMethod; return this; }
        public Builder idpSessionId(String idpSessionId) { this.idpSessionId = idpSessionId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public KeltaSession build() {
            KeltaSession session = new KeltaSession(email, tenantId, tenantSlug, profileId,
                    profileName, displayName, groups, authSource, createdAt);
            session.setAuthMethod(authMethod);
            session.setIdpSessionId(idpSessionId);
            return session;
        }
    }
}
