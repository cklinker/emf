package io.kelta.auth.federation;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Shared constants + redirect validation for the SP-initiated SAML Single Logout
 * flow ({@link io.kelta.auth.controller.SamlLogoutController} initiates,
 * {@link SamlLogoutSuccessHandler} completes).
 *
 * <p>The {@code post_logout_redirect_uri} the browser hands the initiator is echoed
 * back as a top-level redirect after the IdP round-trip, so it MUST be validated to
 * prevent an open redirect. The only legitimate target is the platform's own OIDC
 * {@code end_session_endpoint} — i.e. a URL on the Authorization Server's own origin.
 * We therefore accept only absolute {@code http(s)} URLs whose origin matches the
 * server's own origin (the configured issuer, falling back to the request origin).
 */
public final class SamlLogoutSupport {

    private SamlLogoutSupport() {}

    /** Session attribute holding the validated post-logout redirect target across the IdP round-trip. */
    public static final String POST_LOGOUT_URI_ATTRIBUTE = "io.kelta.auth.SAML_POST_LOGOUT_URI";

    /** Safe default landing when no valid post-logout target is available. */
    public static final String DEFAULT_LOGOUT_SUCCESS_URL = "/login?logout";

    /**
     * Returns {@code candidate} if it is an absolute {@code http(s)} URL on
     * {@code allowedOrigin}; otherwise {@code null}.
     *
     * @param candidate     the caller-supplied redirect target
     * @param allowedOrigin the server's own origin ({@code scheme://host[:port]})
     */
    public static String sanitizeRedirect(String candidate, String allowedOrigin) {
        if (candidate == null || candidate.isBlank() || allowedOrigin == null) {
            return null;
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            if (uri.getHost() == null) {
                return null;
            }
            return originOf(uri).equalsIgnoreCase(allowedOrigin) ? candidate : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * The origin ({@code scheme://host[:port]}) to accept redirects for: the
     * configured issuer's origin when present and parseable, else the request's own
     * origin (scheme + host + non-default port).
     */
    public static String allowedOrigin(String issuerUri, HttpServletRequest request) {
        if (issuerUri != null && !issuerUri.isBlank()) {
            try {
                URI issuer = new URI(issuerUri);
                if (issuer.getHost() != null) {
                    return originOf(issuer);
                }
            } catch (URISyntaxException ignored) {
                // fall through to the request origin
            }
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort || port <= 0 ? "" : ":" + port);
    }

    private static String originOf(URI uri) {
        int port = uri.getPort();
        return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase()
                + (port > 0 ? ":" + port : "");
    }
}
