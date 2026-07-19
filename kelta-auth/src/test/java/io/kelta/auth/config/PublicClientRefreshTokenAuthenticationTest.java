package io.kelta.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Public-client refresh_token client authentication")
class PublicClientRefreshTokenAuthenticationTest {

    private static final String CLIENT_ID = "kelta-platform";

    @Nested
    @DisplayName("Converter")
    class Converter {

        private final PublicClientRefreshTokenAuthenticationConverter converter =
                new PublicClientRefreshTokenAuthenticationConverter();

        private MockHttpServletRequest refreshRequest() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
            request.setParameter(OAuth2ParameterNames.GRANT_TYPE,
                    AuthorizationGrantType.REFRESH_TOKEN.getValue());
            request.setParameter(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID);
            request.setParameter(OAuth2ParameterNames.REFRESH_TOKEN, "some-refresh-token");
            return request;
        }

        @Test
        @DisplayName("converts a secret-less refresh_token request to an unauthenticated NONE token")
        void convertsRefreshRequest() {
            var authentication = (OAuth2ClientAuthenticationToken) converter.convert(refreshRequest());

            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isEqualTo(CLIENT_ID);
            assertThat(authentication.getClientAuthenticationMethod())
                    .isEqualTo(ClientAuthenticationMethod.NONE);
            assertThat(authentication.getAdditionalParameters())
                    .containsEntry(OAuth2ParameterNames.GRANT_TYPE,
                            AuthorizationGrantType.REFRESH_TOKEN.getValue())
                    .containsEntry(OAuth2ParameterNames.REFRESH_TOKEN, "some-refresh-token")
                    .doesNotContainKey(OAuth2ParameterNames.CLIENT_ID);
        }

        @Test
        @DisplayName("ignores non-refresh grant types")
        void ignoresOtherGrantTypes() {
            MockHttpServletRequest request = refreshRequest();
            request.setParameter(OAuth2ParameterNames.GRANT_TYPE,
                    AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
            assertThat(converter.convert(request)).isNull();
        }

        @Test
        @DisplayName("ignores requests with an Authorization header (confidential client)")
        void ignoresAuthorizationHeader() {
            MockHttpServletRequest request = refreshRequest();
            request.addHeader(HttpHeaders.AUTHORIZATION, "Basic Y2xpZW50OnNlY3JldA==");
            assertThat(converter.convert(request)).isNull();
        }

        @Test
        @DisplayName("ignores requests with a client_secret parameter (confidential client)")
        void ignoresClientSecretParam() {
            MockHttpServletRequest request = refreshRequest();
            request.setParameter(OAuth2ParameterNames.CLIENT_SECRET, "s3cret");
            assertThat(converter.convert(request)).isNull();
        }

        @Test
        @DisplayName("ignores requests without client_id")
        void ignoresMissingClientId() {
            MockHttpServletRequest request = refreshRequest();
            request.removeParameter(OAuth2ParameterNames.CLIENT_ID);
            assertThat(converter.convert(request)).isNull();
        }

        @Test
        @DisplayName("rejects duplicate client_id values")
        void rejectsDuplicateClientId() {
            MockHttpServletRequest request = refreshRequest();
            request.setParameter(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID, "other-client");
            assertThatThrownBy(() -> converter.convert(request))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .extracting(ex -> ((OAuth2AuthenticationException) ex).getError().getErrorCode())
                    .isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("Provider")
    class Provider {

        private RegisteredClientRepository repository;
        private PublicClientRefreshTokenAuthenticationProvider provider;

        @BeforeEach
        void setUp() {
            repository = Mockito.mock(RegisteredClientRepository.class);
            provider = new PublicClientRefreshTokenAuthenticationProvider(repository);
        }

        private RegisteredClient publicClient() {
            return RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(CLIENT_ID)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:5173/auth/callback")
                    .build();
        }

        private OAuth2ClientAuthenticationToken refreshAuthentication(String clientId) {
            return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null,
                    Map.of(OAuth2ParameterNames.GRANT_TYPE,
                            AuthorizationGrantType.REFRESH_TOKEN.getValue()));
        }

        @Test
        @DisplayName("authenticates a registered public client for the refresh grant")
        void authenticatesPublicClient() {
            Mockito.when(repository.findByClientId(CLIENT_ID)).thenReturn(publicClient());

            var result = (OAuth2ClientAuthenticationToken) provider
                    .authenticate(refreshAuthentication(CLIENT_ID));

            assertThat(result).isNotNull();
            assertThat(result.isAuthenticated()).isTrue();
            assertThat(result.getRegisteredClient()).isNotNull();
            assertThat(result.getRegisteredClient().getClientId()).isEqualTo(CLIENT_ID);
            assertThat(result.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        }

        @Test
        @DisplayName("passes through non-NONE client authentication methods")
        void passesThroughConfidentialMethods() {
            var authentication = new OAuth2ClientAuthenticationToken(CLIENT_ID,
                    ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "secret",
                    Map.of(OAuth2ParameterNames.GRANT_TYPE,
                            AuthorizationGrantType.REFRESH_TOKEN.getValue()));
            assertThat(provider.authenticate(authentication)).isNull();
        }

        @Test
        @DisplayName("passes through PKCE authorization_code requests to the built-in provider")
        void passesThroughPkceRequests() {
            var authentication = new OAuth2ClientAuthenticationToken(CLIENT_ID,
                    ClientAuthenticationMethod.NONE, null,
                    Map.of(OAuth2ParameterNames.GRANT_TYPE,
                            AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                            OAuth2ParameterNames.CODE, "abc"));
            assertThat(provider.authenticate(authentication)).isNull();
        }

        @Test
        @DisplayName("rejects an unknown client_id")
        void rejectsUnknownClient() {
            Mockito.when(repository.findByClientId("nope")).thenReturn(null);
            assertInvalidClient(refreshAuthentication("nope"));
        }

        @Test
        @DisplayName("rejects a confidential client attempting a secret-less refresh")
        void rejectsConfidentialClientWithoutSecret() {
            RegisteredClient confidential = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("superset")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("https://superset.example.com/callback")
                    .build();
            Mockito.when(repository.findByClientId("superset")).thenReturn(confidential);

            assertInvalidClient(refreshAuthentication("superset"));
        }

        @Test
        @DisplayName("rejects a public client without the refresh_token grant")
        void rejectsClientWithoutRefreshGrant() {
            RegisteredClient noRefresh = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(CLIENT_ID)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost:5173/auth/callback")
                    .build();
            Mockito.when(repository.findByClientId(CLIENT_ID)).thenReturn(noRefresh);

            assertInvalidClient(refreshAuthentication(CLIENT_ID));
        }

        private void assertInvalidClient(OAuth2ClientAuthenticationToken authentication) {
            assertThatThrownBy(() -> provider.authenticate(authentication))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .extracting(ex -> ((OAuth2AuthenticationException) ex).getError().getErrorCode())
                    .isEqualTo(OAuth2ErrorCodes.INVALID_CLIENT);
        }
    }
}
