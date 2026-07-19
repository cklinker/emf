package io.kelta.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authenticates public clients (e.g. the platform SPA) on {@code grant_type=refresh_token}
 * token requests.
 *
 * <p>Spring Authorization Server's built-in {@code PublicClientAuthenticationConverter}
 * only matches PKCE {@code authorization_code} token requests, so a public client
 * presenting a refresh token has no client-authentication path at all: the request
 * falls through the client-auth filter unauthenticated and is redirected to the HTML
 * login page. This converter closes that gap by producing an unauthenticated
 * {@link OAuth2ClientAuthenticationToken} with method {@link ClientAuthenticationMethod#NONE}
 * for refresh-token requests that carry a {@code client_id} and no client secret.
 *
 * <p>Requests that present any secret material (an {@code Authorization} header or a
 * {@code client_secret} parameter) are left to the confidential-client converters, so
 * connected apps and Superset are unaffected.
 *
 * @see PublicClientRefreshTokenAuthenticationProvider
 */
public final class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue()
                .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        // Confidential clients authenticate with their secret — not our concern.
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null) {
            return null;
        }

        Map<String, String[]> parameterMap = request.getParameterMap();
        String[] clientIdValues = parameterMap.get(OAuth2ParameterNames.CLIENT_ID);
        if (clientIdValues == null || !StringUtils.hasText(clientIdValues[0])) {
            return null;
        }
        if (clientIdValues.length != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        String clientId = clientIdValues[0];

        Map<String, Object> additionalParameters = new HashMap<>();
        parameterMap.forEach((key, values) -> {
            if (!OAuth2ParameterNames.CLIENT_ID.equals(key)) {
                additionalParameters.put(key, values.length == 1 ? values[0] : List.of(values));
            }
        });

        return new OAuth2ClientAuthenticationToken(
                clientId, ClientAuthenticationMethod.NONE, null, additionalParameters);
    }
}
