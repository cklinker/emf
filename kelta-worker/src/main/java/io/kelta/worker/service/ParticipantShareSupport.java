package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Grants record-level access by writing {@code record_share} rows when a user
 * becomes a participant of a record (telehealth slice 1,
 * {@code specs/telehealth/1-portal-identity.md}). Later slices' hooks call this
 * when e.g. a portal user is added to a chat conversation or an appointment is
 * booked — the existing {@code RecordShareAccessService} Cerbos widening then
 * makes the record visible to them with no new authz machinery.
 *
 * <p>Idempotent: re-granting an existing (record, user, level) share is a
 * no-op, so participant hooks can fire on every save without duplicating rows.
 * Queries run under the request's tenant-bound transaction (RLS-scoped).
 */
@Service
public class ParticipantShareSupport {

    private static final Logger log = LoggerFactory.getLogger(ParticipantShareSupport.class);

    private final JdbcTemplate jdbcTemplate;

    public ParticipantShareSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Grants {@code userId} the given access level on one record of the named
     * collection. Returns true when a new share row was written, false when an
     * equivalent grant already existed or the collection is unknown.
     */
    public boolean grant(String collectionName, String recordId, String userId, String accessLevel) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("ParticipantShareSupport.grant called without tenant context — skipping");
            return false;
        }

        List<String> collectionIds = jdbcTemplate.queryForList(
                "SELECT id FROM collection WHERE name = ? LIMIT 1", String.class, collectionName);
        if (collectionIds.isEmpty()) {
            log.warn("Cannot grant participant share — unknown collection '{}'", collectionName);
            return false;
        }
        String collectionId = collectionIds.get(0);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO record_share (id, tenant_id, collection_id, record_id,
                    shared_with_id, shared_with_type, access_level, reason, created_at, updated_at)
                SELECT ?, ?, ?, ?, ?, 'USER', ?, 'participant', NOW(), NOW()
                WHERE NOT EXISTS (
                    SELECT 1 FROM record_share
                    WHERE collection_id = ? AND record_id = ?
                      AND shared_with_type = 'USER' AND shared_with_id = ?
                      AND access_level = ?
                )
                """,
                UUID.randomUUID().toString(), tenantId, collectionId, recordId,
                userId, accessLevel,
                collectionId, recordId, userId, accessLevel);
        return inserted > 0;
    }

    /**
     * Revokes a participant grant written by {@link #grant}. Only removes
     * rows carrying the {@code participant} reason so manually created shares
     * survive participant removal.
     */
    public boolean revoke(String collectionName, String recordId, String userId) {
        List<String> collectionIds = jdbcTemplate.queryForList(
                "SELECT id FROM collection WHERE name = ? LIMIT 1", String.class, collectionName);
        if (collectionIds.isEmpty()) {
            return false;
        }
        int deleted = jdbcTemplate.update(
                "DELETE FROM record_share WHERE collection_id = ? AND record_id = ? "
                        + "AND shared_with_type = 'USER' AND shared_with_id = ? AND reason = 'participant'",
                collectionIds.get(0), recordId, userId);
        return deleted > 0;
    }
}
