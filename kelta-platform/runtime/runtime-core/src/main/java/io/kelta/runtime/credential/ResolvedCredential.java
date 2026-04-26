package io.kelta.runtime.credential;

import java.time.Instant;
import java.util.Map;

/**
 * A decrypted credential ready to be used by an action handler at runtime.
 *
 * <p>{@code secretFields} is the decrypted secret material (e.g., access token,
 * client secret, password). It must never be logged, persisted, or returned via
 * any API surface.
 *
 * <p>{@code metadataFields} is the public, non-secret companion data
 * (e.g., header name for an API key, token URL for OAuth). Safe to log in
 * non-sensitive forms.
 */
public record ResolvedCredential(
    String id,
    String name,
    String type,
    Map<String, Object> secretFields,
    Map<String, Object> metadataFields,
    Instant resolvedAt
) {

    /**
     * Returns the decrypted secret value for {@code key}, or {@code null} if not present.
     */
    public Object secret(String key) {
        return secretFields.get(key);
    }

    /**
     * Returns the metadata value for {@code key}, or {@code null} if not present.
     */
    public Object metadata(String key) {
        return metadataFields.get(key);
    }

    /** Redact-friendly toString — never leaks decrypted material. */
    @Override
    public String toString() {
        return "ResolvedCredential{id=" + id + ", name=" + name
            + ", type=" + type + ", resolvedAt=" + resolvedAt + '}';
    }
}
