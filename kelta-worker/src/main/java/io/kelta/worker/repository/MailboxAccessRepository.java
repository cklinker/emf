package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code mailbox_access} — who may see and act on a mailbox.
 *
 * <p>Membership is granted at the mailbox and inherited by every thread, which is what
 * makes the mailbox shared. It is also the sole source of approval authority: a
 * {@code MANAGER} may approve a drafted reply, and no system permission confers that,
 * because a global "may approve" would reach mailboxes the holder is not a member of.
 *
 * @since 1.0.0
 */
@Repository
public class MailboxAccessRepository {

    private final JdbcTemplate jdbcTemplate;

    public MailboxAccessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listForMailbox(String tenantId, String mailboxId) {
        return jdbcTemplate.queryForList("""
                SELECT * FROM mailbox_access
                 WHERE tenant_id = ? AND mailbox_id = ?
                 ORDER BY principal_type, principal_id
                """, tenantId, mailboxId);
    }

    public Optional<Map<String, Object>> findById(String id, String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM mailbox_access WHERE id = ? AND tenant_id = ?", id, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Grants access, or updates the role if the principal already has a grant.
     *
     * <p>Upsert rather than insert-or-fail: re-granting with a different role is the
     * natural way an admin expresses "change this person's role", and making that a
     * conflict would force a delete-then-add dance that briefly removes their access.
     *
     * @return the row id, whether newly created or pre-existing
     */
    public String grant(String tenantId, String mailboxId, String principalType,
                        String principalId, String role, String actor) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO mailbox_access
                    (id, tenant_id, mailbox_id, principal_type, principal_id, role,
                     created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (mailbox_id, principal_type, principal_id)
                DO UPDATE SET role = EXCLUDED.role, updated_by = EXCLUDED.updated_by, updated_at = NOW()
                """, id, tenantId, mailboxId, principalType, principalId, role,
                actor == null ? "" : actor, actor == null ? "" : actor);

        List<String> ids = jdbcTemplate.queryForList("""
                SELECT id FROM mailbox_access
                 WHERE tenant_id = ? AND mailbox_id = ? AND principal_type = ? AND principal_id = ?
                """, String.class, tenantId, mailboxId, principalType, principalId);
        return ids.isEmpty() ? id : ids.getFirst();
    }

    public int revoke(String id, String tenantId) {
        return jdbcTemplate.update(
                "DELETE FROM mailbox_access WHERE id = ? AND tenant_id = ?", id, tenantId);
    }

    public int revokeAllForMailbox(String mailboxId, String tenantId) {
        return jdbcTemplate.update(
                "DELETE FROM mailbox_access WHERE mailbox_id = ? AND tenant_id = ?", mailboxId, tenantId);
    }

    /**
     * Every role the user holds on a mailbox, whether granted directly or through a group.
     *
     * <p>Returns all matching roles rather than one: a user can be both a direct {@code AGENT}
     * and a {@code MANAGER} via a group, and the caller resolves most-permissive-wins the same
     * way the platform does elsewhere. Collapsing here would silently pick a winner.
     *
     * <p>Empty means no access. Callers must treat that as 404 rather than 403 — a 403 confirms
     * the mailbox exists and turns the endpoint into an enumeration oracle.
     *
     * <p><b>Direct group membership only.</b> {@code group_membership.member_type} also allows
     * {@code GROUP}, so groups can nest, and this query does not walk that tree. A user who is
     * only a member of a nested subgroup will not be matched. Resolving nesting needs a
     * recursive CTE with cycle detection; until a tenant actually nests groups for a mailbox
     * grant, under-granting is the safe failure — it denies access rather than inventing it.
     */
    public List<String> rolesForUser(String tenantId, String mailboxId, String userId) {
        return jdbcTemplate.queryForList("""
                SELECT a.role
                  FROM mailbox_access a
                 WHERE a.tenant_id = ?
                   AND a.mailbox_id = ?
                   AND (
                        (a.principal_type = 'USER'  AND a.principal_id = ?)
                     OR (a.principal_type = 'GROUP' AND a.principal_id IN (
                            SELECT gm.group_id FROM group_membership gm
                             WHERE gm.tenant_id = ?
                               AND gm.member_type = 'USER'
                               AND gm.member_id = ?))
                   )
                """, String.class, tenantId, mailboxId, userId, tenantId, userId);
    }

    /** Mailbox ids the user can see at all, direct or via a group. */
    public List<String> accessibleMailboxIds(String tenantId, String userId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT a.mailbox_id
                  FROM mailbox_access a
                 WHERE a.tenant_id = ?
                   AND (
                        (a.principal_type = 'USER'  AND a.principal_id = ?)
                     OR (a.principal_type = 'GROUP' AND a.principal_id IN (
                            SELECT gm.group_id FROM group_membership gm
                             WHERE gm.tenant_id = ?
                               AND gm.member_type = 'USER'
                               AND gm.member_id = ?))
                   )
                """, String.class, tenantId, userId, tenantId, userId);
    }
}
