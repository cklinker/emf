package io.kelta.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the tenant slug from the redirect_uri of an /oauth2/authorize request
 * and stores it in the HTTP session as "tenantId" so KeltaUserDetailsService can
 * scope the user lookup to the correct tenant.
 *
 * Path pattern: /{tenant-slug}/auth/callback
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Pattern TENANT_SLUG_PATTERN =
            Pattern.compile("^/([^/]+)/auth/callback$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())
                && "/oauth2/authorize".equals(request.getServletPath())) {
            String redirectUri = request.getParameter("redirect_uri");
            if (redirectUri != null && !redirectUri.isBlank()) {
                String slug = extractTenantSlug(redirectUri);
                if (slug != null) {
                    request.getSession().setAttribute("tenantId", slug);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractTenantSlug(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            String path = uri.getPath();
            if (path == null) return null;
            Matcher m = TENANT_SLUG_PATTERN.matcher(path);
            return m.matches() ? m.group(1) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
