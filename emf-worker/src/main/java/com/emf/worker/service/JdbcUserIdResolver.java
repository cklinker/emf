package com.emf.worker.service;

import com.emf.runtime.router.UserIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves user identifiers (email addresses) to platform_user UUIDs using JDBC.
 *
 * <p>System collections (flow, dashboard, report, etc.) have foreign key constraints
 * from {@code created_by}/{@code updated_by} to {@code platform_user(id)}.
 * The gateway forwards the JWT subject (typically an email address) in the
 * {@code X-User-Id} header, so this resolver translates that email to the
 * corresponding {@code platform_user.id} UUID.
 *
 * <p>Results are cached per (tenantId, email) pair for the lifetime of the
 * application to avoid repeated lookups.
 */
@Component
public class JdbcUserIdResolver implements UserIdResolver {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserIdResolver.class);

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

        String cacheKey = tenantId + ":" + userIdentifier;
        return cache.computeIfAbsent(cacheKey, k -> lookupUserId(userIdentifier, tenantId));
    }

    private String lookupUserId(String email, String tenantId) {
        try {
            String userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM platform_user WHERE tenant_id = ? AND email = ? LIMIT 1",
                    String.class, tenantId, email);
            if (userId != null) {
                log.debug("Resolved user '{}' to platform_user UUID '{}' for tenant '{}'",
                        email, userId, tenantId);
                return userId;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user '{}' for tenant '{}': {}",
                    email, tenantId, e.getMessage());
        }
        // Return original identifier as fallback
        return email;
    }
}
