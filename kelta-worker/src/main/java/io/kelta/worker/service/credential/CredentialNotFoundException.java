package io.kelta.worker.service.credential;

/**
 * Thrown when a credential reference cannot be resolved within the current tenant.
 * Action handlers can map this to a flow error code so users can write
 * targeted catch policies (e.g., {@code Credential.NotFound}).
 */
public class CredentialNotFoundException extends RuntimeException {

    public CredentialNotFoundException(String tenantId, String reference) {
        super("Credential '" + reference + "' not found in tenant " + tenantId);
    }
}
