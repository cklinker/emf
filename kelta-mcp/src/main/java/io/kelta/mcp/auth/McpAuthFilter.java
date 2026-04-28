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

/**
 * Servlet filter for MCP HTTP endpoints.
 *
 * <p>Lightweight gate — runs on any URL containing {@code /mcp/} and:
 * <ol>
 *   <li>Requires {@code Authorization: Bearer klt_*}. Cryptographic
 *       validation happens at the gateway on the first forwarded API
 *       call; here we only ensure the header is well-formed.</li>
 *   <li>Strips client-supplied {@code X-Tenant-ID} / {@code X-Tenant-Slug}
 *       / {@code X-User-Id} headers so a malicious client can't claim
 *       a tenant other than the one bound to the URL.</li>
 * </ol>
 *
 * <p>URL-pattern matching and slug extraction happen in
 * {@code KeltaMcpController} via Spring MVC {@code @PathVariable},
 * which keeps the slug in the URL throughout the request lifecycle —
 * no rewriting, no forward.
 */
@Component
public class McpAuthFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(McpAuthFilter.class);

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PAT_PREFIX = "klt_";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
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
        chain.doFilter(new SanitizedRequest(request), response);
    }

    private static void unauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("WWW-Authenticate",
                "Bearer realm=\"kelta-mcp\", error=\"" + code + "\"");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /**
     * Wrapper that hides client-supplied tenant headers — the tenant
     * is bound to the URL slug ({@code /{tenantSlug}/mcp/...}), not
     * headers. Without this, a malicious client could try
     * {@code X-Tenant-ID: <other-tenant>} to escape its own tenant.
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
}
