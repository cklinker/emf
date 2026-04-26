package io.kelta.worker.service.api;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.module.integration.spi.IdempotencyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * JDBC implementation of {@link IdempotencyStore}. Lookups and writes both
 * happen inside a {@code TenantContext.callWithTenant(...)} so the V128 RLS
 * policy filters by tenant_id.
 */
@Service
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcIdempotencyStore.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CachedResponse> lookup(String tenantId, String key) {
        return TenantContext.callWithTenant(tenantId, () -> {
            try {
                CachedResponse row = jdbcTemplate.queryForObject(
                    """
                    SELECT status_code, response_body, response_hash
                    FROM   api_call_idempotency
                    WHERE  tenant_id = ? AND idempotency_key = ?
                       AND expires_at > NOW()
                    """,
                    (rs, n) -> new CachedResponse(
                        rs.getInt("status_code"),
                        rs.getString("response_body"),
                        rs.getString("response_hash")),
                    tenantId, key);
                return Optional.ofNullable(row);
            } catch (EmptyResultDataAccessException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public void record(String tenantId, String key, String flowRunId, String stateName,
                        int statusCode, String responseBody, Duration ttl) {
        TenantContext.runWithTenant(tenantId, () -> {
            String hash = sha256(responseBody);
            Timestamp now = Timestamp.from(Instant.now());
            Timestamp expires = Timestamp.from(Instant.now().plus(ttl));
            try {
                jdbcTemplate.update(
                    """
                    INSERT INTO api_call_idempotency (
                        tenant_id, idempotency_key, flow_run_id, state_name,
                        status_code, response_body, response_hash, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, idempotency_key)
                    DO UPDATE SET status_code = EXCLUDED.status_code,
                                  response_body = EXCLUDED.response_body,
                                  response_hash = EXCLUDED.response_hash,
                                  flow_run_id = EXCLUDED.flow_run_id,
                                  state_name = EXCLUDED.state_name,
                                  created_at = EXCLUDED.created_at,
                                  expires_at = EXCLUDED.expires_at
                    """,
                    tenantId, key, flowRunId, stateName,
                    statusCode, responseBody, hash, now, expires);
            } catch (Exception e) {
                log.warn("Failed to record idempotency entry for {}: {}", key, e.getMessage());
            }
        });
    }

    private static String sha256(String value) {
        if (value == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }
}
