package io.kelta.worker.service.credential;

/**
 * Thrown when the credential blob cannot be decrypted or parsed. Usually
 * indicates a key rotation gone wrong or a corrupted ciphertext.
 */
public class CredentialDecryptException extends RuntimeException {

    public CredentialDecryptException(String credentialId, Throwable cause) {
        super("Failed to decrypt credential " + credentialId, cause);
    }
}
