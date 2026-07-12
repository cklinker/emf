package io.kelta.worker.service;

import io.kelta.runtime.router.UserIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Resolves user identifiers (email addresses) to platform_user UUIDs using JDBC.
 *
 * <p>System collections (flow, dashboard, report, etc.) have foreign key constraints
 * from {@code created_by}/{@code updated_by} to {@code platform_user(id)}.
 * The gateway forwards the authenticated user's email in the {@code X-User-Id}
 * header for JWT logins (and the UUID itself for PAT requests), so this
 * resolver translates emails to the corresponding {@code platform_user.id}
 * UUID and passes UUIDs through without a lookup.
 *
 * <p><strong>Only successful resolutions are cached.</strong> A failed lookup
 * (row not visible yet, transient DB error) returns the raw identifier as a
 * best-effort fallback but is never remembered. The previous version cached
 * that fallback for the process lifetime: one lookup racing a portal-user
 * insert permanently poisoned the pod, telehealth endpoints then filtered on
 * {@code portal_user_id = <email>}, and the user's own appointments read as
 * empty on that pod until the next deploy (2026-07-12).
 */
@Component
public class JdbcUserIdResolver implements UserIdResolver {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserIdResolver.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public JdbcUserIdResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String resolve(String userIdentifier, String tenantId) {
        if (userIdentifier == null || userIdentifier.isBlank()) {
            return userIdentifier;
        }
        if (tenantId == null || tenantId.isBlank()) {
            return userIdentifier;
        }
        // PAT paths already carry the platform_user UUID — nothing to resolve.
        if (UUID_PATTERN.matcher(userIdentifier).matches()) {
            return userIdentifier;
        }

        String cacheKey = tenantId + ":" + userIdentifier;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String userId = lookupUserId(userIdentifier, tenantId);
        if (userId != null) {
            // Successes only — a miss must stay retryable (see class doc).
            cache.put(cacheKey, userId);
            return userId;
        }
        // Best-effort fallback, deliberately uncached.
        return userIdentifier;
    }

    private String lookupUserId(String email, String tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM platform_user WHERE tenant_id = ? AND email = ? LIMIT 1",
                    String.class, tenantId, email);
        } catch (Exception e) {
            log.warn("Failed to resolve user '{}' for tenant '{}': {}",
                    email, tenantId, e.getMessage());
            return null;
        }
    }
}
