package io.kelta.mcp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter for MCP HTTP endpoints.
 *
 * <p>The MCP URL convention follows the rest of the Kelta platform:
 * <pre>{@code
 *   /{tenantSlug}/mcp/user
 *   /{tenantSlug}/mcp/admin
 * }</pre>
 * The slug binds the request to a specific tenant — different PATs in
 * different tenants get different MCP URLs in their Claude Code config,
 * so a single deployment can serve any number of tenants.
 *
 * <p>This filter:
 * <ol>
 *   <li>Matches the URL pattern, extracts the slug, and rewrites the
 *       request to the canonical {@code /mcp/(user|admin)} path the SDK
 *       servlet is registered at. The slug rides along as a request
 *       attribute the {@link KeltaTransportContextExtractor} reads.</li>
 *   <li>Enforces presence of {@code Authorization: Bearer klt_*} —
 *       cryptographic validation happens at the gateway on the first
 *       forwarded API call. We just gate the session.</li>
 *   <li>Strips client-supplied tenant headers so a client can't
 *       impersonate another tenant.</li>
 * </ol>
 */
@Component
public class McpAuthFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(McpAuthFilter.class);

    /** Request attribute carrying the slug extracted from the URL. */
    public static final String SLUG_ATTRIBUTE = "kelta.mcp.tenantSlug";

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PAT_PREFIX = "klt_";

    /**
     * Matches {@code /{tenantSlug}/mcp/(user|admin)[/...]}. The slug
     * pattern mirrors {@code TenantSlugExtractionFilter} on the gateway
     * so an invalid slug is rejected here rather than later.
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^/(?<slug>[a-z][a-z0-9-]{1,61}[a-z0-9])/mcp/(?<profile>user|admin)(?<rest>/.*)?$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.contains("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Matcher m = URL_PATTERN.matcher(request.getRequestURI());
        if (!m.matches()) {
            unauthorized(response, "invalid_mcp_path",
                    "Path must be /{tenantSlug}/mcp/(user|admin)");
            return;
        }
        String slug = m.group("slug");
        String profile = m.group("profile");
        String rest = m.group("rest");
        String canonicalPath = "/mcp/" + profile + (rest == null ? "" : rest);

        String header = request.getHeader(AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            unauthorized(response, "missing_bearer", "Missing Authorization: Bearer header");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length());
        if (!token.startsWith(PAT_PREFIX)) {
            unauthorized(response, "invalid_token_prefix", "Token must be a Kelta PAT (klt_*)");
            return;
        }

        request.setAttribute(SLUG_ATTRIBUTE, slug);
        RequestPatHolder.set(token);
        try {
            chain.doFilter(new RewrittenRequest(new SanitizedRequest(request), canonicalPath), response);
        } finally {
            RequestPatHolder.clear();
        }
    }

    private static void unauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        int status = "invalid_mcp_path".equals(code) ? HttpStatus.NOT_FOUND.value()
                : HttpStatus.UNAUTHORIZED.value();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            response.setHeader("WWW-Authenticate",
                    "Bearer realm=\"kelta-mcp\", error=\"" + code + "\"");
        }
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /**
     * Wrapper that hides client-supplied tenant headers — the tenant
     * is bound to the URL slug, not headers. Without this, a malicious
     * client could try {@code X-Tenant-ID: <other-tenant>} to escape
     * its own tenant.
     */
    private static final class SanitizedRequest extends HttpServletRequestWrapper {
        private static final java.util.Set<String> STRIPPED = java.util.Set.of(
                "x-tenant-id", "x-tenant-slug", "x-user-id");

        SanitizedRequest(HttpServletRequest req) {
            super(req);
        }

        @Override
        public String getHeader(String name) {
            if (name != null && STRIPPED.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            if (name != null && STRIPPED.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                return java.util.Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaderNames() {
            java.util.List<String> names = new java.util.ArrayList<>();
            java.util.Enumeration<String> e = super.getHeaderNames();
            while (e.hasMoreElements()) {
                String n = e.nextElement();
                if (!STRIPPED.contains(n.toLowerCase(java.util.Locale.ROOT))) {
                    names.add(n);
                }
            }
            return java.util.Collections.enumeration(names);
        }
    }

    /**
     * Wrapper that rewrites the request URI / servlet path to the
     * canonical SDK-registered path {@code /mcp/(user|admin)} after
     * the slug has been extracted. The SDK servlet's path matching
     * (and any session-id correlation) operates on this rewritten URI.
     */
    private static final class RewrittenRequest extends HttpServletRequestWrapper {
        private final String canonicalPath;

        RewrittenRequest(HttpServletRequest req, String canonicalPath) {
            super(req);
            this.canonicalPath = canonicalPath;
        }

        @Override
        public String getRequestURI() {
            return canonicalPath;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            url.append(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            if (port > 0
                    && (("http".equalsIgnoreCase(getScheme()) && port != 80)
                    || ("https".equalsIgnoreCase(getScheme()) && port != 443))) {
                url.append(':').append(port);
            }
            url.append(canonicalPath);
            return url;
        }

        @Override
        public String getServletPath() {
            return canonicalPath;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
