package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "connected_app_token")
public class ConnectedAppToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_app_id", nullable = false)
    private ConnectedApp connectedApp;

    @Column(name = "token_hash", nullable = false, length = 200)
    private String tokenHash;

    @Column(name = "scopes", columnDefinition = "jsonb")
    private String scopes;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked")
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public ConnectedAppToken() { super(); }

    public ConnectedApp getConnectedApp() { return connectedApp; }
    public void setConnectedApp(ConnectedApp connectedApp) { this.connectedApp = connectedApp; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
