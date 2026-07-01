package io.kelta.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Persists metadata for access tokens minted for connected apps via the
 * OAuth2 {@code client_credentials} grant.
 *
 * <p>The Spring Authorization Server issues the JWT; it does not, on its own,
 * record anything in the connected-app tables. This recorder is invoked from
 * {@link KeltaTokenCustomizer} at the single choke point where a machine token
 * is actually issued so that:
 * <ul>
 *   <li>the token appears in the app's "Tokens" list ({@code connected_app_token}),</li>
 *   <li>the app's {@code last_used_at} reflects real activity, and</li>
 *   <li>an audit row ({@code connected_app_audit}, action {@code TOKEN_ISSUED}) is written.</li>
 * </ul>
 *
 * <p>Recording is best-effort: any failure here is logged and swallowed so that
 * a database hiccup can never prevent a valid token from being issued.
 *
 * <p>{@code connected_app_token} and {@code connected_app_audit} carry no RLS
 * (see V77), so these writes run under kelta-auth's platform connection without
 * a tenant context — the same connection that already reads {@code connected_app}.
 *
 * @since 1.0.0
 */
@Component
public class ConnectedAppTokenRecorder {

    private static final Logger log = LoggerFactory.getLogger(ConnectedAppTokenRecorder.class);

    /** Fallback TTL when the registered client does not define one. */
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final JdbcTemplate jdbcTemplate;

    public ConnectedAppTokenRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records a freshly issued client_credentials access token.
     *
     * @param appId       connected_app id
     * @param tenantId    owning tenant id (for the audit row)
     * @param scopesJson  the app's scopes as a JSON array string (e.g. {@code ["api"]})
     * @param jti         the JWT id; used as the token row id so a later revoke can match it
     * @param accessTtl   the access token time-to-live; may be {@code null}
     */
    public void recordIssuedToken(String appId, String tenantId, String scopesJson,
                                  String jti, Duration accessTtl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtl != null ? accessTtl : DEFAULT_TTL);
        String scopes = (scopesJson == null || scopesJson.isBlank()) ? "[]" : scopesJson;

        try {
            jdbcTemplate.update(
                    "INSERT INTO connected_app_token " +
                            "(id, connected_app_id, token_hash, scopes, issued_at, expires_at, revoked) " +
                            "VALUES (?, ?, ?, ?::jsonb, ?, ?, false)",
                    jti, appId, sha256Hex(jti), scopes,
                    Timestamp.from(now), Timestamp.from(expiresAt)
            );

            jdbcTemplate.update(
                    "UPDATE connected_app SET last_used_at = ?, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), Timestamp.from(now), appId
            );

            jdbcTemplate.update(
                    "INSERT INTO connected_app_audit " +
                            "(id, tenant_id, connected_app_id, action, details, performed_by, created_at) " +
                            "VALUES (?, ?, ?, 'TOKEN_ISSUED', ?::jsonb, NULL, ?)",
                    java.util.UUID.randomUUID().toString(), tenantId, appId,
                    "{\"tokenId\":\"" + jti + "\",\"via\":\"client_credentials\"}",
                    Timestamp.from(now)
            );

            log.debug("Recorded client_credentials token for connected app {} (jti={})", appId, jti);
        } catch (Exception e) {
            // Never fail token issuance because bookkeeping failed.
            log.warn("Failed to record client_credentials token for connected app {}: {}", appId, e.getMessage());
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 is guaranteed present; fall back to the raw value if it somehow is not.
            return value;
        }
    }
}
