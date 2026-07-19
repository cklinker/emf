package io.kelta.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Completes client authentication for public-client refresh-token requests converted by
 * {@link PublicClientRefreshTokenAuthenticationConverter}.
 *
 * <p>A public client has no secret to verify; the refresh token itself is the credential
 * and is validated (and rotated — {@code reuseRefreshTokens(false)}) downstream by
 * {@code OAuth2RefreshTokenAuthenticationProvider}. This provider therefore only verifies
 * that the client exists, is registered as public ({@link ClientAuthenticationMethod#NONE}),
 * and is allowed the {@code refresh_token} grant. Confidential clients that omit their
 * secret are rejected here — never silently authenticated.
 *
 * <p>Returns {@code null} for anything that is not a public-client refresh-token request
 * so the built-in providers (e.g. PKCE) keep handling their cases.
 */
public final class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log =
            LoggerFactory.getLogger(PublicClientRefreshTokenAuthenticationProvider.class);

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientRefreshTokenAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuthentication =
                (OAuth2ClientAuthenticationToken) authentication;

        if (!ClientAuthenticationMethod.NONE.equals(clientAuthentication.getClientAuthenticationMethod())) {
            return null;
        }
        Object grantType = clientAuthentication.getAdditionalParameters()
                .get(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            // PKCE authorization_code requests are handled by PublicClientAuthenticationProvider.
            return null;
        }

        String clientId = clientAuthentication.getPrincipal().toString();
        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            throw invalidClient("client_id");
        }
        if (!registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            // Confidential client attempting a secret-less refresh — reject.
            log.debug("Rejected secret-less refresh_token request for confidential client '{}'", clientId);
            throw invalidClient("authentication_method");
        }
        if (!registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw invalidClient("authorization_grant_type");
        }

        return new OAuth2ClientAuthenticationToken(
                registeredClient, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static OAuth2AuthenticationException invalidClient(String parameterName) {
        OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT,
                "Client authentication failed: " + parameterName,
                "https://datatracker.ietf.org/doc/html/rfc6749#section-3.2.1");
        return new OAuth2AuthenticationException(error);
    }
}
