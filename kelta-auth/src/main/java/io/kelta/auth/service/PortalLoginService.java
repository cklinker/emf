package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Passwordless magic-link login for PORTAL users (telehealth slice 1,
 * {@code specs/telehealth/1-portal-identity.md}).
 *
 * <p>Tokens are 256-bit random values stored only as SHA-256 hashes in
 * {@code portal_login_token} (created by worker migration V167). PORTAL_LOGIN
 * tokens expire after 15 minutes, PORTAL_INVITE tokens (issued by the worker
 * with the invite email) after 7 days; both are single-use — consumption is an
 * atomic conditional UPDATE. Request handling is enumeration-safe: the caller
 * renders the same "check your email" response whether or not the address
 * matches a portal user, and issuance is rate-limited per user.
 *
 * <p>Portal users have no {@code user_credential} row, so the password form
 * cannot authenticate them; this flow is their only entry point. MFA/TOTP is
 * not interposed — possession of the emailed link is the factor, and portal
 * users have no access to MFA enrollment surfaces.
 */
@Service
public class PortalLoginService {

    private static final Logger log = LoggerFactory.getLogger(PortalLoginService.class);
    private static final Logger audit = LoggerFactory.getLogger("security.audit");

    private static final Duration LOGIN_LINK_EXPIRY = Duration.ofMinutes(15);
    private static final int MAX_LINKS_PER_WINDOW = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final WorkerClient workerClient;

    public PortalLoginService(JdbcTemplate jdbcTemplate, WorkerClient workerClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.workerClient = workerClient;
    }

    /**
     * Resolves a session tenant value (UUID or slug) to the tenant UUID, or
     * empty when unknown.
     */
    public Optional<String> resolveTenantUuid(String uuidOrSlug) {
        if (uuidOrSlug == null || uuidOrSlug.isBlank()) {
            return Optional.empty();
        }
        if (uuidOrSlug.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return Optional.of(uuidOrSlug);
        }
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM tenant WHERE slug = ?", String.class, uuidOrSlug);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /**
     * Issues a sign-in link for the portal user with this email, if one exists.
     * Always returns without revealing whether the address matched — failures
     * are logged/audited server-side only.
     *
     * @param verifyBaseUrl absolute URL of the verify endpoint (derived from the
     *                      current request so custom-domain logins stay on-host)
     */
    public void requestLink(String tenantId, String email, String verifyBaseUrl) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (tenantId == null || normalizedEmail.isBlank()) {
            return;
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                """
                SELECT pu.id, t.name AS tenant_name
                FROM platform_user pu JOIN tenant t ON t.id = pu.tenant_id
                WHERE pu.email = ? AND pu.tenant_id = ?
                  AND pu.status = 'ACTIVE' AND pu.user_type = 'PORTAL'
                """, normalizedEmail, tenantId);
        if (users.isEmpty()) {
            audit.info("security_event=PORTAL_LINK_REQUESTED actor={} target=unknown tenant={} result=ignored detail=no_matching_portal_user",
                    normalizedEmail, tenantId);
            return;
        }
        String userId = String.valueOf(users.get(0).get("id"));
        String tenantName = String.valueOf(users.get(0).getOrDefault("tenant_name", "Kelta"));

        Integer recent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portal_login_token WHERE tenant_id = ? AND user_id = ? "
                        + "AND purpose = 'PORTAL_LOGIN' AND created_at > ?",
                Integer.class, tenantId, userId,
                Timestamp.from(Instant.now().minus(LOGIN_LINK_EXPIRY)));
        if (recent != null && recent >= MAX_LINKS_PER_WINDOW) {
            audit.warn("security_event=PORTAL_LINK_REQUESTED actor={} target={} tenant={} result=failure detail=rate_limited",
                    normalizedEmail, userId, tenantId);
            return;
        }

        String rawToken = generateToken();
        jdbcTemplate.update(
                "INSERT INTO portal_login_token (id, tenant_id, user_id, token_hash, purpose, "
                        + "expires_at, created_at) VALUES (?, ?, ?, ?, 'PORTAL_LOGIN', ?, NOW())",
                UUID.randomUUID().toString(), tenantId, userId, sha256(rawToken),
                Timestamp.from(Instant.now().plus(LOGIN_LINK_EXPIRY)));

        String actionUrl = verifyBaseUrl
                + (verifyBaseUrl.contains("?") ? "&" : "?") + "token=" + rawToken;
        boolean queued = workerClient.sendTemplateEmail(tenantId, normalizedEmail, "portal.login-link",
                Map.of("tenantName", tenantName, "actionUrl", actionUrl, "expiresIn", "15 minutes"),
                "PORTAL_LOGIN", userId);
        audit.info("security_event=PORTAL_LINK_REQUESTED actor={} target={} tenant={} result={}",
                normalizedEmail, userId, tenantId, queued ? "success" : "failure");
    }

    /**
     * The tenant's allowlisted headless landing URLs
     * ({@code tenant.settings.portalAuth.redirectUris}, slice 8). Read
     * per-request — no cache, so no cross-pod invalidation to keep consistent.
     * Empty when the tenant is unknown or nothing is configured.
     */
    public List<String> portalRedirectUris(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT jsonb_array_elements_text(settings#>'{portalAuth,redirectUris}') "
                        + "FROM tenant WHERE id = ?",
                String.class, tenantId);
    }

    public record PortalVerification(KeltaUserDetails userDetails, String tenantSlug) {}

    /**
     * Atomically consumes a login/invite token and loads the portal user.
     * Empty on unknown, expired, already-consumed tokens, or when the user is
     * no longer an ACTIVE portal user.
     */
    public Optional<PortalVerification> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        List<Map<String, Object>> consumed = jdbcTemplate.queryForList(
                "UPDATE portal_login_token SET consumed_at = NOW() "
                        + "WHERE token_hash = ? AND consumed_at IS NULL AND expires_at > NOW() "
                        + "RETURNING user_id, tenant_id",
                sha256(rawToken));
        if (consumed.isEmpty()) {
            audit.warn("security_event=PORTAL_LOGIN actor=unknown target=unknown tenant=unknown result=failure detail=invalid_or_expired_token");
            return Optional.empty();
        }
        String userId = String.valueOf(consumed.get(0).get("user_id"));
        String tenantId = String.valueOf(consumed.get(0).get("tenant_id"));

        List<PortalVerification> loaded = jdbcTemplate.query(
                """
                SELECT pu.id, pu.email, pu.tenant_id, pu.profile_id,
                       p.name AS profile_name,
                       COALESCE(pu.first_name || ' ' || pu.last_name, pu.email) AS display_name,
                       pu.user_type, t.slug AS tenant_slug
                FROM platform_user pu
                LEFT JOIN profile p ON p.id = pu.profile_id
                JOIN tenant t ON t.id = pu.tenant_id
                WHERE pu.id = ? AND pu.tenant_id = ?
                  AND pu.status = 'ACTIVE' AND pu.user_type = 'PORTAL'
                """,
                (rs, rowNum) -> new PortalVerification(
                        new KeltaUserDetails(
                                rs.getString("id"),
                                rs.getString("email"),
                                rs.getString("tenant_id"),
                                rs.getString("profile_id"),
                                rs.getString("profile_name"),
                                rs.getString("display_name"),
                                "",
                                true,
                                false,
                                false,
                                rs.getString("user_type")),
                        rs.getString("tenant_slug")),
                userId, tenantId);
        if (loaded.isEmpty()) {
            audit.warn("security_event=PORTAL_LOGIN actor=unknown target={} tenant={} result=failure detail=user_inactive_or_not_portal",
                    userId, tenantId);
            return Optional.empty();
        }
        audit.info("security_event=PORTAL_LOGIN actor={} target={} tenant={} result=success",
                loaded.get(0).userDetails().getEmail(), userId, tenantId);
        return Optional.of(loaded.get(0));
    }

    static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
