package io.kelta.worker.service;

import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.runtime.storage.CredentialProvider;
import io.kelta.worker.repository.EnvironmentRepository;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Remote promotion client: URL allow-listing, remote-environment guards, and
 * vault credential resolution. No live HTTP — every case fails before a
 * request could be sent.
 */
@DisplayName("RemotePromotionClient")
class RemotePromotionClientTest {

    private static final String TENANT = "t1";

    private EnvironmentRepository environmentRepository;
    private CredentialProvider credentialProvider;
    private RemotePromotionClient client;

    @BeforeEach
    void setUp() {
        environmentRepository = mock(EnvironmentRepository.class);
        credentialProvider = mock(CredentialProvider.class);
        client = new RemotePromotionClient(environmentRepository, credentialProvider,
                new ObjectMapper());
    }

    private static Map<String, Object> remoteEnv(String baseUrl, String credentialRef) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("id", "env-r");
        env.put("remote_base_url", baseUrl);
        env.put("remote_tenant_slug", "acme");
        env.put("credential_ref", credentialRef);
        return env;
    }

    private static ResolvedCredential credential(Map<String, Object> secrets) {
        return new ResolvedCredential("cred-1", "remote-pat", "API_KEY",
                secrets, Map.of(), Instant.now());
    }

    // ------------------------------------------------------------------
    // validateRemoteBaseUrl
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("validateRemoteBaseUrl")
    class ValidateRemoteBaseUrl {

        @Test
        @DisplayName("accepts https URLs")
        void acceptsHttps() {
            assertThatCode(() -> RemotePromotionClient.validateRemoteBaseUrl("https://remote.example.com"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts http URLs")
        void acceptsHttp() {
            assertThatCode(() -> RemotePromotionClient.validateRemoteBaseUrl("http://remote.example.com:8080"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects non-http schemes")
        void rejectsFtp() {
            assertThatThrownBy(() -> RemotePromotionClient.validateRemoteBaseUrl("ftp://remote.example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("http or https");
        }

        @Test
        @DisplayName("rejects embedded userinfo")
        void rejectsUserinfo() {
            assertThatThrownBy(() ->
                    RemotePromotionClient.validateRemoteBaseUrl("https://user:pass@remote.example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not embed credentials");
        }

        @Test
        @DisplayName("rejects URLs without a host")
        void rejectsMissingHost() {
            assertThatThrownBy(() -> RemotePromotionClient.validateRemoteBaseUrl("https:///api"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must include a host");
        }

        @Test
        @DisplayName("rejects unparseable URLs")
        void rejectsGarbage() {
            assertThatThrownBy(() -> RemotePromotionClient.validateRemoteBaseUrl("ht tp://bad url"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------
    // requireRemoteEnvironment
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("requireRemoteEnvironment")
    class RequireRemoteEnvironment {

        @Test
        @DisplayName("rejects an unknown environment")
        void rejectsUnknownEnv() {
            when(environmentRepository.findByIdAndTenant("env-x", TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> client.requireRemoteEnvironment("env-x", TENANT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Environment not found");
        }

        @Test
        @DisplayName("rejects an environment without a remote base URL")
        void rejectsLocalEnv() {
            Map<String, Object> localEnv = new LinkedHashMap<>();
            localEnv.put("id", "env-l");
            localEnv.put("sandbox_tenant_id", "sbx");
            when(environmentRepository.findByIdAndTenant("env-l", TENANT))
                    .thenReturn(Optional.of(localEnv));

            assertThatThrownBy(() -> client.requireRemoteEnvironment("env-l", TENANT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a remote target");
        }

        @Test
        @DisplayName("returns a well-formed remote environment")
        void returnsRemoteEnv() {
            Map<String, Object> env = remoteEnv("https://remote.example.com", "vault-1");
            when(environmentRepository.findByIdAndTenant("env-r", TENANT)).thenReturn(Optional.of(env));

            assertThat(client.requireRemoteEnvironment("env-r", TENANT)).isSameAs(env);
        }
    }

    // ------------------------------------------------------------------
    // Credential resolution + URL building (exercised through pushPackage,
    // which throws before any network I/O in every case below)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("pushPackage pre-flight failures")
    class PushPackagePreFlight {

        private final Map<String, Object> pkg = Map.of("items", java.util.List.of());

        @Test
        @DisplayName("rejects an environment whose stored base URL is no longer allowed")
        void rejectsBadStoredUrl() {
            assertThatThrownBy(() -> client.pushPackage(
                    remoteEnv("ftp://remote.example.com", "vault-1"), pkg, "SKIP", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("http or https");
        }

        @Test
        @DisplayName("rejects an environment without a credentialRef")
        void rejectsMissingCredentialRef() {
            assertThatThrownBy(() -> client.pushPackage(
                    remoteEnv("https://remote.example.com", null), pkg, "SKIP", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no credentialRef");
        }

        @Test
        @DisplayName("rejects a credentialRef that is not in the vault")
        void rejectsUnknownCredential() {
            when(credentialProvider.resolve("vault-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> client.pushPackage(
                    remoteEnv("https://remote.example.com", "vault-1"), pkg, "SKIP", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Credential not found in vault: vault-1");
        }

        @Test
        @DisplayName("rejects a credential without a usable token secret key")
        void rejectsUnusableCredential() {
            when(credentialProvider.resolve("vault-1"))
                    .thenReturn(Optional.of(credential(Map.of("something-else", "x"))));

            assertThatThrownBy(() -> client.pushPackage(
                    remoteEnv("https://remote.example.com", "vault-1"), pkg, "SKIP", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no usable token secret");
        }

        @Test
        @DisplayName("rejects a blank token secret")
        void rejectsBlankToken() {
            when(credentialProvider.resolve("vault-1"))
                    .thenReturn(Optional.of(credential(Map.of("token", "  "))));

            assertThatThrownBy(() -> client.pushPackage(
                    remoteEnv("https://remote.example.com", "vault-1"), pkg, "SKIP", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no usable token secret");
        }
    }

    @Test
    @DisplayName("testConnection reports a friendly failure when the credential is missing")
    void testConnectionReportsCredentialFailure() {
        Map<String, Object> env = remoteEnv("https://remote.invalid", "vault-1");
        when(environmentRepository.findByIdAndTenant("env-r", TENANT)).thenReturn(Optional.of(env));
        when(credentialProvider.resolve("vault-1")).thenReturn(Optional.empty());

        var result = client.testConnection("env-r", TENANT);

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(result.get("error"))).contains("Credential not found in vault");
    }
}
