package io.kelta.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import io.kelta.auth.config.AuthorizationServerConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConnectedAppClientSynchronizer Tests")
class ConnectedAppClientSynchronizerTest {

    private ConnectedAppClientSynchronizer synchronizer;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RegisteredClientRepository clientRepository;

    @BeforeEach
    void setUp() {
        synchronizer = new ConnectedAppClientSynchronizer(jdbcTemplate, clientRepository);
    }

    @Test
    @DisplayName("should register connected app with authorization_code grant type")
    void shouldRegisterAuthCodeApp() {
        Map<String, Object> app = Map.of(
                "id", "app-1",
                "client_id", "my-app",
                "client_secret_hash", "$2a$10$hashvalue",
                "name", "My Third Party App",
                "redirect_uris", "[\"https://myapp.com/callback\"]",
                "scopes", "[\"api\", \"read:records\"]",
                "grant_types", "[\"authorization_code\", \"client_credentials\"]",
                "require_pkce", false,
                "consent_required", true
        );

        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(app));
        when(clientRepository.findByClientId("my-app")).thenReturn(null);

        synchronizer.synchronizeClients();

        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(clientRepository).save(captor.capture());

        RegisteredClient registered = captor.getValue();
        assertThat(registered.getClientId()).isEqualTo("my-app");
        assertThat(registered.getClientName()).isEqualTo("My Third Party App");
        assertThat(registered.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.AUTHORIZATION_CODE,
                         AuthorizationGrantType.CLIENT_CREDENTIALS,
                         AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(registered.getRedirectUris()).contains("https://myapp.com/callback");
        assertThat(registered.getScopes()).contains("openid", "profile", "email", "api", "read:records");
        assertThat(registered.getClientSettings().isRequireAuthorizationConsent()).isTrue();
        assertThat(registered.getClientSettings().isRequireProofKey()).isFalse();
        assertThat(registered.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                         ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Test
    @DisplayName("should register public PKCE client without secret")
    void shouldRegisterPkceApp() {
        Map<String, Object> app = Map.of(
                "id", "app-2",
                "client_id", "spa-app",
                "client_secret_hash", "",
                "name", "SPA App",
                "redirect_uris", "[\"https://spa.example.com/callback\"]",
                "scopes", "[\"api\"]",
                "grant_types", "[\"authorization_code\"]",
                "require_pkce", true,
                "consent_required", true
        );

        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(app));
        when(clientRepository.findByClientId("spa-app")).thenReturn(null);

        synchronizer.synchronizeClients();

        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(clientRepository).save(captor.capture());

        RegisteredClient registered = captor.getValue();
        assertThat(registered.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(registered.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.NONE);
    }

    @Test
    @DisplayName("should skip already registered clients")
    void shouldSkipExistingClients() {
        Map<String, Object> app = Map.of(
                "id", "app-1",
                "client_id", "existing-app",
                "client_secret_hash", "$2a$10$hash",
                "name", "Existing App",
                "redirect_uris", "[\"https://existing.com/callback\"]",
                "scopes", "[\"api\"]",
                "grant_types", "[\"authorization_code\"]",
                "require_pkce", false,
                "consent_required", true
        );

        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(app));
        when(clientRepository.findByClientId("existing-app"))
                .thenReturn(RegisteredClient.withId("existing-id")
                        .clientId("existing-app")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("https://existing.com/callback")
                        .build());

        synchronizer.synchronizeClients();

        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("should handle empty app list gracefully")
    void shouldHandleEmptyList() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        synchronizer.synchronizeClients();

        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("stored connected-app secret authenticates with the production password encoder (no invalid_client)")
    void connectedAppSecretMatchesProductionEncoder() {
        // The connected_app table stores a pre-computed bcrypt hash of the secret.
        String plaintextSecret = "s3cr3t-client-secret";
        String bcryptHash = new BCryptPasswordEncoder().encode(plaintextSecret);

        Map<String, Object> app = Map.of(
                "id", "app-1",
                "client_id", "couchpicks-web",
                "client_secret_hash", bcryptHash,
                "name", "CouchPicks Web",
                "redirect_uris", "[\"https://couchpicks.example.com/callback\"]",
                "scopes", "[\"api\"]",
                "grant_types", "[\"authorization_code\"]",
                "require_pkce", false,
                "consent_required", false
        );

        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(app));
        when(clientRepository.findByClientId("couchpicks-web")).thenReturn(null);

        synchronizer.synchronizeClients();

        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(clientRepository).save(captor.capture());
        RegisteredClient registered = captor.getValue();

        // Synchronizer stores the secret with a {bcrypt} prefix for the delegating encoder.
        assertThat(registered.getClientSecret()).startsWith("{bcrypt}");

        // The production password encoder must accept the plaintext secret against the
        // stored value; otherwise CLIENT_SECRET_BASIC/POST auth fails with invalid_client.
        PasswordEncoder encoder = new AuthorizationServerConfig().passwordEncoder();
        assertThat(encoder.matches(plaintextSecret, registered.getClientSecret())).isTrue();
        assertThat(encoder.matches("wrong-secret", registered.getClientSecret())).isFalse();
    }

    @Test
    @DisplayName("production password encoder still matches legacy bare-bcrypt secrets (Superset/internal back-compat)")
    void productionEncoderMatchesBareBcryptSecret() {
        // ConnectedAppRegistrar stores Superset/internal secrets via passwordEncoder.encode(),
        // which historically produced a BARE bcrypt hash (no {bcrypt} prefix). Those rows must
        // keep authenticating after switching to the delegating encoder.
        String plaintextSecret = "superset-secret";
        String bareBcrypt = new BCryptPasswordEncoder().encode(plaintextSecret);
        assertThat(bareBcrypt).doesNotStartWith("{");

        PasswordEncoder encoder = new AuthorizationServerConfig().passwordEncoder();
        assertThat(encoder.matches(plaintextSecret, bareBcrypt)).isTrue();
        assertThat(encoder.matches("wrong-secret", bareBcrypt)).isFalse();
    }
}
