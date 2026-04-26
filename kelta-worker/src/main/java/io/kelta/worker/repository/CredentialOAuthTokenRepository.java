package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the {@code credential_oauth_token} table — one row per
 * OAuth-typed credential, holding the encrypted access/refresh tokens and
 * expiry metadata.
 */
@Repository
public class CredentialOAuthTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public CredentialOAuthTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findByCredentialId(String credentialId, String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, credential_id, tenant_id, access_token_enc, refresh_token_enc, "
                + "token_type, expires_at, refreshed_at, refresh_failure_count, "
                + "last_refresh_error, scope "
                + "FROM credential_oauth_token "
                + "WHERE credential_id = ? AND tenant_id = ?",
            credentialId, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Inserts or updates the token row for a credential. */
    public void upsert(String credentialId, String tenantId,
                       String accessTokenEnc, String refreshTokenEnc,
                       String tokenType, Instant expiresAt, String scope) {
        Optional<Map<String, Object>> existing = findByCredentialId(credentialId, tenantId);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            jdbcTemplate.update(
                "UPDATE credential_oauth_token SET access_token_enc = ?, "
                    + "refresh_token_enc = COALESCE(?, refresh_token_enc), "
                    + "token_type = ?, expires_at = ?, refreshed_at = ?, "
                    + "refresh_failure_count = 0, last_refresh_error = NULL, "
                    + "scope = ?, updated_at = ? "
                    + "WHERE credential_id = ? AND tenant_id = ?",
                accessTokenEnc, refreshTokenEnc, tokenType,
                Timestamp.from(expiresAt), Timestamp.from(now),
                scope, Timestamp.from(now), credentialId, tenantId);
        } else {
            jdbcTemplate.update(
                "INSERT INTO credential_oauth_token (id, credential_id, tenant_id, "
                    + "access_token_enc, refresh_token_enc, token_type, expires_at, "
                    + "refreshed_at, scope, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), credentialId, tenantId,
                accessTokenEnc, refreshTokenEnc, tokenType,
                Timestamp.from(expiresAt), Timestamp.from(now), scope,
                Timestamp.from(now), Timestamp.from(now));
        }
    }

    public void recordRefreshFailure(String credentialId, String tenantId, String error) {
        jdbcTemplate.update(
            "UPDATE credential_oauth_token "
                + "SET refresh_failure_count = refresh_failure_count + 1, "
                + "    last_refresh_error = ?, updated_at = ? "
                + "WHERE credential_id = ? AND tenant_id = ?",
            error, Timestamp.from(Instant.now()), credentialId, tenantId);
    }
}
