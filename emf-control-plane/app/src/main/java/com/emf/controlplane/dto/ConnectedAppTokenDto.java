package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ConnectedAppToken;

import java.time.Instant;

public class ConnectedAppTokenDto {

    private String id;
    private String scopes;
    private Instant issuedAt;
    private Instant expiresAt;
    private boolean revoked;

    public static ConnectedAppTokenDto fromEntity(ConnectedAppToken entity) {
        ConnectedAppTokenDto dto = new ConnectedAppTokenDto();
        dto.setId(entity.getId());
        dto.setScopes(entity.getScopes());
        dto.setIssuedAt(entity.getIssuedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setRevoked(entity.isRevoked());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
