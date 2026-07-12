package io.kelta.worker.service;

import io.kelta.runtime.module.integration.spi.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and invites external portal users (telehealth slice 1,
 * {@code specs/telehealth/1-portal-identity.md}).
 *
 * <p>Portal users are passwordless: no {@code user_credential} row is written,
 * which structurally excludes them from the form-login path
 * ({@code KeltaUserDetailsService} inner-joins {@code user_credential}). Their
 * only entry point is the magic-link flow served by kelta-auth against
 * {@code portal_login_token}. The invite email carries a single-use
 * PORTAL_INVITE link (7 days); later sign-ins use PORTAL_LOGIN links
 * (15 minutes) requested from the portal login page.
 *
 * <p>Re-inviting an existing PORTAL user issues a fresh link instead of
 * failing, so admins can always recover a lost/expired invite.
 */
@Service
public class PortalUserService {

    private static final Logger log = LoggerFactory.getLogger(PortalUserService.class);
    private static final Duration INVITE_EXPIRY = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;
    private final TenantQuotaResolver quotaResolver;
    private final String authBaseUrl;

    public PortalUserService(JdbcTemplate jdbcTemplate,
                             EmailService emailService,
                             TenantQuotaResolver quotaResolver,
                             @Value("${kelta.auth.issuer-uri:}") String authBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        this.quotaResolver = quotaResolver;
        this.authBaseUrl = authBaseUrl;
    }

    public record PortalInviteResult(String userId, boolean created) {}

    /**
     * Creates (or re-invites) a portal user and emails a single-use sign-in
     * link. Enforces the {@code maxPortalUsers} governor on first create.
     *
     * @throws ResponseStatusException 409 when the email belongs to an INTERNAL
     *         user, 429 when the portal-seat governor is exhausted, 500 when the
     *         Portal User profile is missing (V167 seeds it).
     */
    public PortalInviteResult invitePortalUser(String tenantId, String actorId,
                                               String email, String firstName, String lastName) {
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id, user_type FROM platform_user WHERE tenant_id = ? AND email = ?",
                tenantId, normalizedEmail);
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.get(0);
            if (!"PORTAL".equals(row.get("user_type"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A user with this email already exists and is not a portal user");
            }
            String userId = String.valueOf(row.get("id"));
            issueInviteLink(tenantId, userId, normalizedEmail, firstName);
            SecurityAuditLogger.log(SecurityAuditLogger.EventType.PORTAL_USER_INVITED,
                    actorId, userId, tenantId, "success", "re-invite");
            return new PortalInviteResult(userId, false);
        }

        enforcePortalSeatGovernor(tenantId);

        String profileId = findPortalProfileId(tenantId);
        String userId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO platform_user (id, tenant_id, email, username, first_name, last_name, "
                        + "status, user_type, profile_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 'PORTAL', ?, NOW(), NOW())",
                userId, tenantId, normalizedEmail, normalizedEmail,
                firstName, lastName, profileId);

        issueInviteLink(tenantId, userId, normalizedEmail, firstName);
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.PORTAL_USER_INVITED,
                actorId, userId, tenantId, "success", null);
        log.info("Portal user {} invited in tenant {}", userId, tenantId);
        return new PortalInviteResult(userId, true);
    }

    private void enforcePortalSeatGovernor(String tenantId) {
        int max = quotaResolver.intQuota(tenantId, TenantTierQuotas.KEY_MAX_PORTAL_USERS);
        Integer current = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_user WHERE tenant_id = ? AND user_type = 'PORTAL'",
                Integer.class, tenantId);
        if (current != null && current >= max) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Portal user limit reached (" + max + "). Raise maxPortalUsers in governor limits.");
        }
    }

    private String findPortalProfileId(String tenantId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM profile WHERE tenant_id = ? AND name = 'Portal User' LIMIT 1",
                String.class, tenantId);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Portal User profile missing for tenant — V167 seeding did not run");
        }
        return ids.get(0);
    }

    /**
     * Writes a hashed PORTAL_INVITE token and queues the {@code portal.invite}
     * email. The raw token exists only inside the emailed link.
     */
    private void issueInviteLink(String tenantId, String userId, String email, String firstName) {
        String rawToken = PortalTokens.generate();
        jdbcTemplate.update(
                "INSERT INTO portal_login_token (id, tenant_id, user_id, token_hash, purpose, "
                        + "expires_at, created_at) VALUES (?, ?, ?, ?, 'PORTAL_INVITE', ?, NOW())",
                UUID.randomUUID().toString(), tenantId, userId, PortalTokens.sha256(rawToken),
                Timestamp.from(Instant.now().plus(INVITE_EXPIRY)));

        String tenantName = jdbcTemplate.queryForList(
                        "SELECT name FROM tenant WHERE id = ?", String.class, tenantId).stream()
                .findFirst().orElse("Kelta");
        // Headless portals (slice 8) can point invite links at their own
        // allowlisted landing page; default stays the kelta-auth verify page.
        String inviteRedirect = jdbcTemplate.queryForList(
                        "SELECT settings#>>'{portalAuth,inviteRedirectUri}' FROM tenant WHERE id = ?",
                        String.class, tenantId).stream()
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);
        String linkBase;
        if (inviteRedirect != null) {
            linkBase = inviteRedirect;
        } else {
            String base = authBaseUrl.endsWith("/")
                    ? authBaseUrl.substring(0, authBaseUrl.length() - 1) : authBaseUrl;
            linkBase = base + "/portal/login/verify";
        }
        String actionUrl = linkBase + (linkBase.contains("?") ? "&" : "?") + "token=" + rawToken;

        emailService.sendByKey(tenantId, email, "portal.invite",
                Map.of(
                        "tenantName", tenantName,
                        "firstName", firstName == null ? "" : firstName,
                        "actionUrl", actionUrl,
                        "expiresIn", "7 days"),
                "PORTAL_INVITE", userId);
    }

    // Token material moved to PortalTokens (telehealth slice 4) so every
    // portal_login_token issuer produces identical hashes.
    static String generateToken() {
        return PortalTokens.generate();
    }

    static String sha256(String value) {
        return PortalTokens.sha256(value);
    }
}
