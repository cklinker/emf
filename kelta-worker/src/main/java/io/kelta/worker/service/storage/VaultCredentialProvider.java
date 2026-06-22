package io.kelta.worker.service.storage;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.runtime.storage.CredentialProvider;
import io.kelta.worker.service.credential.CredentialNotFoundException;
import io.kelta.worker.service.credential.CredentialResolver;
import io.kelta.worker.service.credential.ResolutionContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bridges the runtime-core {@link CredentialProvider} SPI (used by the external storage
 * adapters) to the worker's credential vault ({@link CredentialResolver}), resolving an
 * external connector's {@code credentialRef} to its decrypted material for the current tenant.
 *
 * <p>The vault resolver only exists when {@code kelta.encryption.key} is configured, so it is
 * injected via {@link ObjectProvider}; when absent (or the tenant/ref is missing, or the
 * credential doesn't exist) this returns empty and the adapter surfaces a clear error rather
 * than connecting with no/partial credentials.
 */
@Component
public class VaultCredentialProvider implements CredentialProvider {

    private final CredentialResolver resolver;

    public VaultCredentialProvider(ObjectProvider<CredentialResolver> resolverProvider) {
        this.resolver = resolverProvider.getIfAvailable();
    }

    @Override
    public Optional<ResolvedCredential> resolve(String credentialRef) {
        if (resolver == null || credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(resolver.resolve(
                    tenantId, credentialRef, ResolutionContext.forUser(null, "EXTERNAL_STORAGE:" + credentialRef)));
        } catch (CredentialNotFoundException e) {
            return Optional.empty();
        }
    }
}
