package com.emf.controlplane.config;

import com.emf.controlplane.service.SecurityAuditService;
import com.emf.controlplane.service.UserService;
import com.emf.controlplane.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter that tracks user logins after successful JWT authentication.
 *
 * <p>This filter runs after Spring Security authenticates the request.
 * For each authenticated request it:
 * <ol>
 *   <li>JIT-provisions or updates the user via {@link UserService#provisionOrUpdate}</li>
 *   <li>Records a login history entry via {@link UserService#recordLogin}</li>
 * </ol>
 *
 * <p>To avoid writing to the database on every API call, a per-user
 * time-based cache ensures that login tracking only fires once per
 * {@value #TRACKING_INTERVAL_SECONDS}-second window.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class LoginTrackingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginTrackingFilter.class);

    /** Minimum interval (in seconds) between successive login tracking writes for the same user. */
    private static final long TRACKING_INTERVAL_SECONDS = 1800; // 30 minutes

    private final UserService userService;
    private final SecurityAuditService auditService;

    /** Maps "tenantId:email" to the epoch-second of the last tracking write. */
    private final Map<String, Long> lastTrackedAt = new ConcurrentHashMap<>();

    public LoginTrackingFilter(UserService userService,
                               @org.springframework.lang.Nullable SecurityAuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            trackLoginIfNeeded(request);
        } catch (Exception e) {
            // Login tracking must never block the request
            log.warn("Login tracking failed (non-fatal): {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private void trackLoginIfNeeded(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String email = extractEmail(jwt);
        if (email == null) {
            return;
        }

        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return;
        }

        // Throttle: only track once per interval per user
        String cacheKey = tenantId + ":" + email;
        long now = Instant.now().getEpochSecond();
        Long lastTime = lastTrackedAt.get(cacheKey);
        if (lastTime != null && (now - lastTime) < TRACKING_INTERVAL_SECONDS) {
            return;
        }

        // JIT provision / update (sets lastLoginAt and increments loginCount)
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        String username = jwt.getClaimAsString("preferred_username");
        var user = userService.provisionOrUpdate(tenantId, email, firstName, lastName, username);

        // Record login history entry
        String sourceIp = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        userService.recordLogin(user.getId(), tenantId, sourceIp, "OAUTH", "SUCCESS",
                userAgent != null ? truncate(userAgent, 500) : null);

        // Audit log the login
        if (auditService != null) {
            auditService.logUserProvisioned(user.getId(), email, "OIDC");
        }

        lastTrackedAt.put(cacheKey, now);
        log.debug("Tracked login for user {} ({}) in tenant {}", user.getId(), email, tenantId);

        // Periodic cleanup of stale cache entries (every ~100 logins)
        if (lastTrackedAt.size() > 1000) {
            evictStaleEntries(now);
        }
    }

    /**
     * Extracts the user's email from the JWT.
     * Tries "email" first, then "preferred_username" (Authentik sends email there).
     */
    private String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        // Some providers use preferred_username for the email
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && preferredUsername.contains("@")) {
            return preferredUsername;
        }
        return null;
    }

    /**
     * Extracts the client IP address, respecting X-Forwarded-For from the gateway.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; the first entry is the client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    /** Removes cache entries older than 2x the tracking interval. */
    private void evictStaleEntries(long nowEpochSecond) {
        long threshold = nowEpochSecond - (TRACKING_INTERVAL_SECONDS * 2);
        lastTrackedAt.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }
}
