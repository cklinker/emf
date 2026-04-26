package io.kelta.runtime.credential;

import tools.jackson.databind.node.ObjectNode;

/**
 * Plaintext input from the UI when creating or updating a credential.
 *
 * <p>The {@code plaintext} node contains both secret fields (which the
 * encryption hook will move into the encrypted blob) and metadata fields
 * (which stay in the plaintext {@code metadata} JSONB).
 *
 * <p>This record is short-lived: instances should never be persisted, logged,
 * or returned in API responses.
 */
public record CredentialMaterial(
    String type,
    ObjectNode plaintext
) {
}
