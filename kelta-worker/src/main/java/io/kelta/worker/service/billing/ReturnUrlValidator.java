package io.kelta.worker.service.billing;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Validates the URLs a member is sent back to after checkout or a billing-portal
 * visit.
 *
 * <p>These arrive in a request body and are handed to the payment processor,
 * which will redirect the member to them. Unvalidated, that is an open redirect
 * with a paying customer on the other end — a plausible phishing hop straight
 * out of a real payment flow. So a URL is accepted only when it is absolute
 * HTTPS and its <b>origin</b> (scheme + host + port) exactly matches one the
 * tenant registered in the credential's {@code allowedReturnOrigins}.
 *
 * <p>Deliberate choices:
 * <ul>
 *   <li><b>Origin comparison, not prefix</b> — a {@code startsWith} check on
 *       {@code https://app.example.com} also accepts
 *       {@code https://app.example.com.evil.test}.</li>
 *   <li><b>Empty allowlist denies everything.</b> A tenant that has not
 *       configured origins cannot redirect anywhere, rather than anywhere at
 *       all being allowed.</li>
 *   <li><b>Userinfo rejected</b> — {@code https://app.example.com@evil.test}
 *       reads as the allowed host to a human and as {@code evil.test} to a
 *       browser.</li>
 *   <li>{@code http://localhost} is permitted only for local development, and
 *       only when the tenant explicitly lists it.</li>
 * </ul>
 */
@Component
public class ReturnUrlValidator {

    /**
     * @return true when {@code url} is absolute HTTPS (or explicitly-allowed
     *         localhost) and its origin exactly matches an allowed origin
     */
    public boolean isAllowed(String url, List<String> allowedOrigins) {
        if (url == null || url.isBlank() || allowedOrigins == null || allowedOrigins.isEmpty()) {
            return false;
        }
        String origin = originOf(url);
        if (origin == null) {
            return false;
        }
        for (String allowed : allowedOrigins) {
            String allowedOrigin = originOf(allowed);
            if (allowedOrigin != null && allowedOrigin.equals(origin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes a URL to {@code scheme://host[:port]}, or null when it is not a
     * usable absolute http(s) URL. Default ports are elided so
     * {@code https://a.test} and {@code https://a.test:443} compare equal.
     */
    String originOf(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (RuntimeException e) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isBlank()) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        host = host.toLowerCase(Locale.ROOT);

        // Credentials in the authority make the real host unreadable to a human.
        if (uri.getUserInfo() != null) {
            return null;
        }
        boolean localhost = "localhost".equals(host) || "127.0.0.1".equals(host);
        if (!"https".equals(scheme) && !("http".equals(scheme) && localhost)) {
            return null;
        }

        int port = uri.getPort();
        boolean defaultPort = port == -1
                || ("https".equals(scheme) && port == 443)
                || ("http".equals(scheme) && port == 80);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
