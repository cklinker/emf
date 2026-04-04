package io.kelta.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlatformRedirectUriValidator Tests")
class PlatformRedirectUriValidatorTest {

    private PlatformRedirectUriValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PlatformRedirectUriValidator();
    }

    @Nested
    @DisplayName("Exact match")
    class ExactMatch {
        @Test
        void shouldAllowExactMatch() {
            var context = buildContext("kelta-platform",
                    "http://localhost:5173/auth/callback",
                    "http://localhost:5173/auth/callback");
            assertThatCode(() -> validator.accept(context)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Platform client tenant-scoped URIs")
    class PlatformClient {
        @Test
        void shouldAllowTenantScopedAuthCallbackWithSameOrigin() {
            var context = buildContext("kelta-platform",
                    "http://localhost:5173/acme-corp/auth/callback",
                    "http://localhost:5173/auth/callback");
            assertThatCode(() -> validator.accept(context)).doesNotThrowAnyException();
        }

        @Test
        void shouldRejectDifferentOrigin() {
            var context = buildContext("kelta-platform",
                    "http://evil.com/acme-corp/auth/callback",
                    "http://localhost:5173/auth/callback");
            assertThatThrownBy(() -> validator.accept(context))
                    .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        }

        @Test
        void shouldRejectPathNotEndingWithAuthCallback() {
            var context = buildContext("kelta-platform",
                    "http://localhost:5173/acme-corp/some-other-path",
                    "http://localhost:5173/auth/callback");
            assertThatThrownBy(() -> validator.accept(context))
                    .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("Non-platform client")
    class NonPlatformClient {
        @Test
        void shouldRejectNonExactMatchForOtherClients() {
            var context = buildContext("other-client",
                    "http://localhost:5173/different/path",
                    "http://localhost:5173/auth/callback");
            assertThatThrownBy(() -> validator.accept(context))
                    .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("Connected app with multiple redirect URIs")
    class ConnectedAppClient {
        @Test
        void shouldAllowExactPathMatchWithSameOrigin() {
            var context = buildContextMultiRedirect("my-connected-app",
                    "https://myapp.com/oauth/callback",
                    "https://myapp.com/oauth/callback",
                    "https://myapp.com/oauth/callback-staging");
            assertThatCode(() -> validator.accept(context)).doesNotThrowAnyException();
        }

        @Test
        void shouldAllowSecondRegisteredRedirectUri() {
            var context = buildContextMultiRedirect("my-connected-app",
                    "https://myapp.com/oauth/callback-staging",
                    "https://myapp.com/oauth/callback",
                    "https://myapp.com/oauth/callback-staging");
            assertThatCode(() -> validator.accept(context)).doesNotThrowAnyException();
        }

        @Test
        void shouldRejectDifferentOriginForConnectedApp() {
            var context = buildContextMultiRedirect("my-connected-app",
                    "https://evil.com/oauth/callback",
                    "https://myapp.com/oauth/callback",
                    "https://myapp.com/oauth/callback-staging");
            assertThatThrownBy(() -> validator.accept(context))
                    .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        }

        @Test
        void shouldRejectDifferentPathForConnectedApp() {
            var context = buildContextMultiRedirect("my-connected-app",
                    "https://myapp.com/other/path",
                    "https://myapp.com/oauth/callback",
                    "https://myapp.com/oauth/callback-staging");
            assertThatThrownBy(() -> validator.accept(context))
                    .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("Null redirect URI")
    class NullRedirectUri {
        @Test
        void shouldReturnEarlyForNullRedirectUri() {
            var context = buildContext("kelta-platform", null, "http://localhost:5173/auth/callback");
            assertThatCode(() -> validator.accept(context)).doesNotThrowAnyException();
        }

        @Test
        void shouldReturnEarlyForBlankRedirectUri() {
            var context = buildContext("kelta-platform", "", "http://localhost:5173/auth/callback");
            assertThatCode(() -> validator.accept(context)).doesNotThrowAnyException();
        }
    }

    private OAuth2AuthorizationCodeRequestAuthenticationContext buildContext(
            String clientId, String requestedRedirectUri, String registeredRedirectUri) {

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(registeredRedirectUri)
                .build();

        OAuth2AuthorizationCodeRequestAuthenticationToken authToken =
                new OAuth2AuthorizationCodeRequestAuthenticationToken(
                        "http://localhost:9000/oauth2/authorize",
                        clientId,
                        new TestingAuthenticationToken("test-principal", null),
                        requestedRedirectUri,
                        "state123",
                        Set.of("openid"),
                        Map.of()
                );

        return OAuth2AuthorizationCodeRequestAuthenticationContext
                .with(authToken)
                .registeredClient(registeredClient)
                .build();
    }

    private OAuth2AuthorizationCodeRequestAuthenticationContext buildContextMultiRedirect(
            String clientId, String requestedRedirectUri, String... registeredRedirectUris) {

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
        for (String uri : registeredRedirectUris) {
            builder.redirectUri(uri);
        }
        RegisteredClient registeredClient = builder.build();

        OAuth2AuthorizationCodeRequestAuthenticationToken authToken =
                new OAuth2AuthorizationCodeRequestAuthenticationToken(
                        "http://localhost:9000/oauth2/authorize",
                        clientId,
                        new TestingAuthenticationToken("test-principal", null),
                        requestedRedirectUri,
                        "state123",
                        Set.of("openid"),
                        Map.of()
                );

        return OAuth2AuthorizationCodeRequestAuthenticationContext
                .with(authToken)
                .registeredClient(registeredClient)
                .build();
    }
}
