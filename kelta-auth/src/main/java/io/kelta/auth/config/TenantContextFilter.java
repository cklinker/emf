package io.kelta.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
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
 * <p>If the incoming tenant slug differs from the one currently stored in the
 * session, the Spring Security context is cleared so the user re-authenticates
 * in the new tenant rather than reusing the principal from the previous one.
 * Without this, a cross-tenant navigation (e.g. /threadline/... → /default/...)
 * would silently issue a token carrying the prior tenant's identity, which the
 * gateway then rejects as a cross-tenant access attempt.
 *
 * <p>Registered as a servlet-level filter (before Spring Security) via {@code @Component}.
 * The method condition (GET + /oauth2/authorize) makes it a no-op for all other requests.
 *
 * Path pattern: /{tenant-slug}/auth/callback
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private static final Pattern TENANT_SLUG_PATTERN =
            Pattern.compile("^/([^/]+)/auth/callback$");

    static final String SESSION_TENANT_ATTR = "tenantId";

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
                    applyTenantContext(request, slug);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void applyTenantContext(HttpServletRequest request, String slug) {
        HttpSession session = request.getSession();
        Object existing = session.getAttribute(SESSION_TENANT_ATTR);
        if (existing instanceof String prev && !prev.isBlank() && !prev.equals(slug)) {
            log.info("Tenant context changing from '{}' to '{}' — clearing stale Spring Security context",
                    prev, slug);
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            SecurityContextHolder.clearContext();
        }
        session.setAttribute(SESSION_TENANT_ATTR, slug);
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
