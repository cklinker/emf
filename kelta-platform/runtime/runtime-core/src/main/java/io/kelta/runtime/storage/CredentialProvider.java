package io.kelta.runtime.storage;

import io.kelta.runtime.credential.ResolvedCredential;

import java.util.Optional;

/**
 * Resolves an external connector's stored credential by reference, for the current
 * tenant. Implemented in the worker as a bridge to the credential vault
 * ({@code CredentialResolver}); kept as a runtime-core SPI so the external storage
 * adapters can resolve secrets without the vault's plaintext ever living in a
 * collection's {@code adapterConfig}.
 *
 * @since 1.0.0
 */
public interface CredentialProvider {

    /**
     * Resolve a credential reference (vault credential name or id) to its decrypted
     * material for the current tenant.
     *
     * @param credentialRef the vault credential name or id
     * @return the resolved credential, or empty if no such credential exists
     */
    Optional<ResolvedCredential> resolve(String credentialRef);
}
