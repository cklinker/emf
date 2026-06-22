package io.kelta.worker.service.storage;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.worker.service.credential.CredentialNotFoundException;
import io.kelta.worker.service.credential.CredentialResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultCredentialProvider")
class VaultCredentialProviderTest {

    private static final String TENANT = "t1";

    @Mock
    private CredentialResolver resolver;

    @Mock
    private ObjectProvider<CredentialResolver> resolverProvider;

    private VaultCredentialProvider withResolver(CredentialResolver r) {
        lenient().when(resolverProvider.getIfAvailable()).thenReturn(r);
        return new VaultCredentialProvider(resolverProvider);
    }

    @Test
    @DisplayName("resolves a credential for the current tenant")
    void resolvesForTenant() {
        ResolvedCredential cred = new ResolvedCredential(
                "c1", "db", "basic_auth", Map.of("username", "u"), Map.of(), Instant.EPOCH);
        when(resolver.resolve(eq(TENANT), eq("vault-ref"), any())).thenReturn(cred);
        VaultCredentialProvider provider = withResolver(resolver);

        Optional<ResolvedCredential> result =
                TenantContext.callWithTenant(TENANT, () -> provider.resolve("vault-ref"));

        assertThat(result).containsSame(cred);
    }

    @Test
    @DisplayName("returns empty when the vault resolver is not configured")
    void emptyWhenNoResolver() {
        VaultCredentialProvider provider = withResolver(null);
        assertThat(TenantContext.callWithTenant(TENANT, () -> provider.resolve("ref"))).isEmpty();
    }

    @Test
    @DisplayName("returns empty when the credential does not exist")
    void emptyWhenNotFound() {
        when(resolver.resolve(any(), any(), any())).thenThrow(new CredentialNotFoundException(TENANT, "ref"));
        VaultCredentialProvider provider = withResolver(resolver);
        assertThat(TenantContext.callWithTenant(TENANT, () -> provider.resolve("ref"))).isEmpty();
    }

    @Test
    @DisplayName("returns empty for a blank reference without hitting the vault")
    void emptyForBlankRef() {
        VaultCredentialProvider provider = withResolver(resolver);
        assertThat(TenantContext.callWithTenant(TENANT, () -> provider.resolve("  "))).isEmpty();
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("returns empty when there is no tenant in context")
    void emptyWhenNoTenant() {
        VaultCredentialProvider provider = withResolver(resolver);
        assertThat(provider.resolve("ref")).isEmpty();
        verifyNoInteractions(resolver);
    }
}
