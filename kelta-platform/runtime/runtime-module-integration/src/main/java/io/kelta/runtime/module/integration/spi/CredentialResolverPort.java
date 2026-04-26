package io.kelta.runtime.module.integration.spi;

import io.kelta.runtime.credential.ResolvedCredential;

/**
 * Module-side bridge to the platform credential resolver. The worker adapts
 * its {@code CredentialResolverImpl} to this interface and registers it as a
 * {@code ModuleContext} extension so handlers (CALL_API, future mailers)
 * never depend on worker-internal types.
 *
 * <p>If no implementation is available, handlers that require a credential
 * fail with {@code Credential.NotConfigured} — the platform was not
 * configured with the credential vault foundation (PR 1) bootstrapped.
 */
public interface CredentialResolverPort {

    /**
     * Returns the decrypted credential for {@code reference} (id or name)
     * scoped to the supplied tenant. {@code purpose} is captured in the
     * platform's audit trail.
     *
     * @throws RuntimeException when the credential cannot be resolved
     */
    ResolvedCredential resolve(String tenantId, String reference, String purpose);
}
