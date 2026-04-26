package io.kelta.runtime.credential;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Result of refreshing an OAuth2 access token. Producers fill in the new
 * {@code accessToken} (always) and {@code refreshToken} (if the IdP rotated it).
 * The {@code rawResponse} is the sanitized token endpoint response — useful for
 * audit/debugging but should not contain decrypted secrets.
 */
public record OAuthRefreshOutcome(
    String accessToken,
    String refreshToken,
    String tokenType,
    Instant expiresAt,
    String scope,
    JsonNode rawResponse
) {
}
