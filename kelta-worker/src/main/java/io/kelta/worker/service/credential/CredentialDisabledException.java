package io.kelta.worker.service.credential;

/**
 * Thrown when a credential is found but its {@code active} flag is false.
 * The flow run should fail clearly rather than silently fall back to no auth.
 */
public class CredentialDisabledException extends RuntimeException {

    public CredentialDisabledException(String credentialId, String name) {
        super("Credential '" + name + "' (" + credentialId + ") is disabled");
    }
}
