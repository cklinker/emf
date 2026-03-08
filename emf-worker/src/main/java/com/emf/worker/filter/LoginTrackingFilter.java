package com.emf.worker.filter;

import com.emf.worker.service.OpenSearchAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet filter that tracks user login activity by recording entries in the
 * {@code login_history} table and updating {@code platform_user} login metadata.
 *
 * <p>The gateway forwards authenticated user identity via headers:
 * <ul>
 *   <li>{@code X-User-Id} — email address from JWT subject claim</li>
 *   <li>{@code X-Tenant-ID} — resolved tenant UUID</li>
 * </ul>
 *
 * <p>To avoid excessive database writes, tracking is throttled to once per
 * {@value #TRACKING_INTERVAL_SECONDS} seconds per user per tenant. This filter
 * is designed to be non-blocking — any tracking failures are logged as warnings
 * and never interrupt the request processing.
 *
 * <p>If no {@code platform_user} record exists for the authenticated email,
 * this filter auto-provisions one using the available header information.
 * This ensures that users authenticating via OIDC for the first time are
 * automatically registered in the platform.
 *
 * <p>On each tracked request, this filter:
 * <ol>
 *   <li>Auto-provisions a {@code platform_user} if none exists for the email</li>
 *   <li>Updates {@code platform_user.last_login_at} and increments {@code login_count}</li>
 *   <li>Inserts a row into {@code login_history}</li>
 *   <li>Inserts a {@code LOGIN_SUCCESS} event into {@code security_audit_log}</li>
 * </ol>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class LoginTrackingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginTrackingFilter.class);

    /** Minimum interval between tracking writes for the same user, in seconds. */
    static final long TRACKING_INTERVAL_SECONDS = 1800; // 30 minutes

    /** Maximum number of cache entries before triggering eviction. */
    private static final int MAX_CACHE_SIZE = 1000;

    /** Maximum length for user agent strings stored in the database. */
    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;
    private final OpenSearchAuditService openSearchAuditService;

    /** Maps "tenantId:email" to the epoch-second of the last tracking write. */
    private final Map<String, Long> lastTrackedAt;

    @Autowired
    public LoginTrackingFilter(JdbcTemplate jdbcTemplate, OpenSearchAuditService openSearchAuditService) {
        this(jdbcTemplate, openSearchAuditService, new ConcurrentHashMap<>());
    }

    /** Constructor for testing — allows injecting the throttle cache. */
    LoginTrackingFilter(JdbcTemplate jdbcTemplate, OpenSearchAuditService openSearchAuditService,
                        Map<String, Long> lastTrackedAt) {
        this.jdbcTemplate = jdbcTemplate;
        this.openSearchAuditService = openSearchAuditService;
        this.lastTrackedAt = lastTrackedAt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {
        try {
            trackLoginIfNeeded(request);
        } catch (Throwable t) {
            // Catch Throwable (not just Exception) to handle NoClassDefFoundError,
            // ExceptionInInitializerError, and other Errors that would otherwise
            // crash normal requests due to OpenSearch/audit failures.
            log.warn("Login tracking failed (non-fatal): {}", t.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private void trackLoginIfNeeded(HttpServletRequest request) {
        String email = request.getHeader("X-User-Id");
        String tenantId = request.getHeader("X-Tenant-ID");

        if (email == null || email.isBlank() || tenantId == null || tenantId.isBlank()) {
            return;
        }

        // X-User-Id contains an email from the JWT sub claim.
        // If it already looks like a UUID, skip tracking (should not happen in normal flow).
        if (isUuid(email)) {
            return;
        }

        // Throttle: only track once per interval per user per tenant
        String cacheKey = tenantId + ":" + email;
        long now = Instant.now().getEpochSecond();
        Long lastTime = lastTrackedAt.get(cacheKey);
        if (lastTime != null && (now - lastTime) < TRACKING_INTERVAL_SECONDS) {
            return;
        }

        // Resolve user UUID from email, auto-provisioning if needed
        String userId = lookupUserId(email, tenantId);
        if (userId == null) {
            String username = request.getHeader("X-Forwarded-User");
            userId = provisionUser(email, tenantId, username);
            if (userId == null) {
                return;
            }
        }

        String sourceIp = extractClientIp(request);
        String userAgent = truncateUserAgent(request.getHeader("User-Agent"));
        Instant loginTime = Instant.now();
        Timestamp ts = Timestamp.from(loginTime);

        // 1. Update platform_user login metadata
        updateUserLoginInfo(userId, ts);

        // 2. Insert login_history row
        insertLoginHistory(userId, tenantId, ts, sourceIp, userAgent);

        // 3. Insert security_audit_log LOGIN event
        insertSecurityAuditLogin(userId, email, tenantId, sourceIp, userAgent);

        // 4. Write to OpenSearch for observability
        openSearchAuditService.logLoginHistory(tenantId, userId, email, "OAUTH", "SUCCESS", sourceIp);
        openSearchAuditService.logSecurityAudit(tenantId, userId, "LOGIN_SUCCESS", "AUTH",
                sourceIp, "{\"provider\":\"OIDC\",\"loginType\":\"OAUTH\"}");

        lastTrackedAt.put(cacheKey, now);
        log.debug("Tracked login for user {} ({}) in tenant {}", userId, email, tenantId);

        // Periodic cleanup of stale cache entries
        if (lastTrackedAt.size() > MAX_CACHE_SIZE) {
            evictStaleEntries(now);
        }
    }

    String lookupUserId(String email, String tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM platform_user WHERE tenant_id = ? AND email = ? LIMIT 1",
                    String.class, tenantId, email);
        } catch (Exception e) {
            log.debug("Could not resolve user '{}' in tenant '{}': {}", email, tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * Auto-provisions a new {@code platform_user} record for a user authenticating
     * for the first time. Uses {@code INSERT ... ON CONFLICT DO NOTHING} for thread
     * safety when multiple concurrent requests arrive for the same new user.
     *
     * @return the new user's UUID, or {@code null} if provisioning failed
     */
    String provisionUser(String email, String tenantId, String username) {
        String id = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update(
                    "INSERT INTO platform_user (id, tenant_id, email, username, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW()) " +
                            "ON CONFLICT (tenant_id, email) DO NOTHING",
                    id, tenantId, email, username);

            // Re-query to get the actual ID (handles race condition where another thread inserted first)
            String resolvedId = lookupUserId(email, tenantId);
            if (resolvedId != null) {
                log.info("Auto-provisioned platform_user for '{}' in tenant '{}'", email, tenantId);
                return resolvedId;
            }
        } catch (Exception e) {
            log.warn("Failed to auto-provision user '{}' in tenant '{}': {}", email, tenantId, e.getMessage());
        }
        return null;
    }

    private void updateUserLoginInfo(String userId, Timestamp ts) {
        jdbcTemplate.update(
                "UPDATE platform_user SET last_login_at = ?, login_count = COALESCE(login_count, 0) + 1, updated_at = ? WHERE id = ?",
                ts, ts, userId);
    }

    private void insertLoginHistory(String userId, String tenantId, Timestamp ts,
                                     String sourceIp, String userAgent) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO login_history (id, user_id, tenant_id, login_time, source_ip, login_type, status, user_agent, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, tenantId, ts, sourceIp, "OAUTH", "SUCCESS", userAgent, ts, ts);
    }

    private void insertSecurityAuditLogin(String userId, String email, String tenantId,
                                           String sourceIp, String userAgent) {
        String id = UUID.randomUUID().toString();
        String details = "{\"provider\":\"OIDC\",\"loginType\":\"OAUTH\"}";
        jdbcTemplate.update(
                "INSERT INTO security_audit_log (id, tenant_id, event_type, event_category, actor_user_id, actor_email, " +
                        "target_type, target_id, target_name, details, ip_address, user_agent, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                id, tenantId, "LOGIN_SUCCESS", "AUTH", userId, email,
                "USER", userId, email, details, sourceIp, userAgent,
                Timestamp.from(Instant.now()));
    }

    static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        if (userAgent.length() > MAX_USER_AGENT_LENGTH) {
            return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return userAgent;
    }

    private void evictStaleEntries(long nowEpochSecond) {
        long threshold = nowEpochSecond - (TRACKING_INTERVAL_SECONDS * 2);
        lastTrackedAt.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        return value.charAt(8) == '-' && value.charAt(13) == '-'
                && value.charAt(18) == '-' && value.charAt(23) == '-';
    }
}
