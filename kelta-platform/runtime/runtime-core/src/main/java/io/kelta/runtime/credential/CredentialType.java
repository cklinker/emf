package io.kelta.runtime.credential;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * Strategy interface for a credential type (api_key, bearer_token, oauth2_*, etc.).
 *
 * <p>Implementations describe:
 * <ul>
 *   <li>The plaintext input fields the UI sends ({@link #getInputSchema()})</li>
 *   <li>Which input fields are secrets (encrypted) vs. metadata (plaintext JSONB)</li>
 *   <li>How to validate plaintext input ({@link #validate(ObjectNode)})</li>
 *   <li>How to test connectivity for a saved credential ({@link #test})</li>
 *   <li>For OAuth2 types, how to refresh access tokens ({@link #refresh})</li>
 * </ul>
 *
 * <p>Implementations are auto-discovered as Spring beans and registered in
 * {@link CredentialTypeRegistry}.
 */
public interface CredentialType {

    /**
     * Stable key identifying this type. Stored in {@code credential.type}.
     * Examples: "api_key", "bearer_token", "oauth2_client_credentials".
     */
    String getKey();

    /**
     * Human-readable name for display in the UI selector.
     */
    String getDisplayName();

    /**
     * Optional one-line description shown in the UI selector.
     */
    default String getDescription() {
        return "";
    }

    /**
     * JSON Schema describing the plaintext input the UI POSTs when creating
     * or updating a credential of this type. Used by the UI to render the form.
     */
    JsonNode getInputSchema();

    /**
     * Names of input fields that are secrets (will be encrypted into the
     * storage blob). All other input fields are treated as metadata.
     */
    Set<String> getSecretFields();

    /**
     * Names of input fields that are metadata (stored plaintext in the
     * {@code metadata} JSONB column). The union of {@link #getSecretFields()}
     * and {@link #getMetadataFields()} should equal the input field set.
     */
    Set<String> getMetadataFields();

    /**
     * Validates plaintext input. Returns a list of validation messages
     * (empty when input is valid). Implementations should check required
     * fields and sane formats — full structural validation is the UI's job.
     *
     * @param plaintext the merged input record (secret + metadata fields)
     */
    default List<String> validate(ObjectNode plaintext) {
        return List.of();
    }

    /**
     * Tests connectivity using the supplied credential material. The result
     * is returned to the UI verbatim — never include decrypted material in
     * the message or details.
     */
    CredentialTestResult test(CredentialMaterial material, ObjectNode metadata);

    /**
     * Whether this type supports OAuth2 token refresh. Default: false.
     */
    default boolean supportsOAuthRefresh() {
        return false;
    }

    /**
     * Refreshes the OAuth2 token using the credential's secrets and the
     * current token state. Only invoked for types where
     * {@link #supportsOAuthRefresh()} returns true.
     *
     * @throws UnsupportedOperationException when refresh is not supported
     */
    default OAuthRefreshOutcome refresh(CredentialMaterial material,
                                        ObjectNode metadata,
                                        OAuthTokenState current) {
        throw new UnsupportedOperationException(
            "Type " + getKey() + " does not support OAuth refresh");
    }
}
