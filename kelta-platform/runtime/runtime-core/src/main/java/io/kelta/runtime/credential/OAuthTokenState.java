package io.kelta.runtime.credential;

import java.time.Instant;

/**
 * Snapshot of an OAuth2 token row for refresh decisions. Decrypted tokens flow
 * through this record only at the moment of an HTTP exchange — never persisted
 * outside the {@code credential_oauth_token} table.
 */
public record OAuthTokenState(
    String accessToken,
    String refreshToken,
    String tokenType,
    Instant expiresAt,
    String scope
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean expiresWithin(java.time.Duration window) {
        return expiresAt != null
            && Instant.now().plus(window).isAfter(expiresAt);
    }
}
