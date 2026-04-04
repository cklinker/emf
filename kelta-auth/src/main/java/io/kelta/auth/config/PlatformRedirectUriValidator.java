package io.kelta.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.net.URI;
import java.util.function.Consumer;

/**
 * Custom redirect URI validator for the multi-tenant platform.
 * <p>
 * Spring Authorization Server requires exact redirect_uri matching, but our UI
 * uses tenant-scoped callback URLs: {@code {origin}/{tenant-slug}/auth/callback}.
 * <p>
 * This validator allows any redirect_uri whose origin matches a registered
 * redirect_uri's origin and whose path ends with {@code /auth/callback}.
 */
public class PlatformRedirectUriValidator
        implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    private static final Logger log = LoggerFactory.getLogger(PlatformRedirectUriValidator.class);

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken authenticationToken =
                context.getAuthentication();
        RegisteredClient registeredClient = context.getRegisteredClient();
        String requestedRedirectUri = authenticationToken.getRedirectUri();

        if (requestedRedirectUri == null || requestedRedirectUri.isBlank()) {
            // No redirect_uri provided — let Spring's default handling resolve it
            return;
        }

        // Check exact match first (standard behavior)
        if (registeredClient.getRedirectUris().contains(requestedRedirectUri)) {
            return;
        }

        // For the platform client, allow any redirect_uri that:
        // 1. Has the same origin as a registered redirect URI
        // 2. Has a path ending with /auth/callback
        if ("kelta-platform".equals(registeredClient.getClientId())) {
            if (isOriginMatchWithSuffix(requestedRedirectUri, registeredClient, "/auth/callback")) {
                return;
            }
        }

        // For connected apps with multiple redirect URIs, validate that the
        // requested URI matches a registered origin + exact path. This supports
        // apps that register multiple callback paths for different environments.
        if (registeredClient.getRedirectUris().size() > 1) {
            if (isExactOriginAndPathMatch(requestedRedirectUri, registeredClient)) {
                return;
            }
        }

        OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                "invalid_redirect_uri", "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1");
        throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, authenticationToken);
    }

    private boolean isOriginMatchWithSuffix(String requestedRedirectUri,
                                            RegisteredClient registeredClient,
                                            String pathSuffix) {
        try {
            URI requested = URI.create(requestedRedirectUri);
            String requestedOrigin = extractOrigin(requested);

            for (String registered : registeredClient.getRedirectUris()) {
                URI registeredUri = URI.create(registered);
                String registeredOrigin = extractOrigin(registeredUri);

                if (requestedOrigin.equals(registeredOrigin)
                        && requested.getPath() != null
                        && requested.getPath().endsWith(pathSuffix)) {
                    log.debug("Allowing tenant-scoped redirect_uri: {}", requestedRedirectUri);
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid URI
        }
        return false;
    }

    private boolean isExactOriginAndPathMatch(String requestedRedirectUri,
                                              RegisteredClient registeredClient) {
        try {
            URI requested = URI.create(requestedRedirectUri);
            String requestedOrigin = extractOrigin(requested);

            for (String registered : registeredClient.getRedirectUris()) {
                URI registeredUri = URI.create(registered);
                String registeredOrigin = extractOrigin(registeredUri);

                if (requestedOrigin.equals(registeredOrigin)
                        && requested.getPath() != null
                        && requested.getPath().equals(registeredUri.getPath())) {
                    log.debug("Allowing connected app redirect_uri with origin+path match: {}",
                            requestedRedirectUri);
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid URI
        }
        return false;
    }

    private static String extractOrigin(URI uri) {
        return uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }
}
