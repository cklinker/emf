package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EnvironmentPromotionRepository {

    private final JdbcTemplate jdbcTemplate;

    public EnvironmentPromotionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public String create(String tenantId, String sourceEnvId, String targetEnvId,
                         String promotionType, String snapshotId, String promotedBy) {
        String id = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO environment_promotion (id, tenant_id, source_env_id, target_env_id, " +
                        "status, promotion_type, snapshot_id, promoted_by, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)",
                id, tenantId, sourceEnvId, targetEnvId, promotionType, snapshotId, promotedBy, now, now
        );
        return id;
    }

    public Optional<Map<String, Object>> findByIdAndTenant(String promotionId, String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT p.id, p.tenant_id, p.source_env_id, p.target_env_id, p.status, " +
                        "p.promotion_type, p.snapshot_id, p.changes_summary, " +
                        "p.items_promoted, p.items_skipped, p.items_failed, " +
                        "p.error_message, p.approved_by, p.approved_at, p.promoted_by, " +
                        "p.started_at, p.completed_at, p.created_at, p.updated_at, " +
                        "se.name as source_env_name, te.name as target_env_name " +
                        "FROM environment_promotion p " +
                        "JOIN environment se ON se.id = p.source_env_id " +
                        "JOIN environment te ON te.id = p.target_env_id " +
                        "WHERE p.id = ? AND p.tenant_id = ?",
                promotionId, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Map<String, Object>> findByTenant(String tenantId, int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT p.id, p.tenant_id, p.source_env_id, p.target_env_id, p.status, " +
                        "p.promotion_type, p.items_promoted, p.items_skipped, p.items_failed, " +
                        "p.promoted_by, p.started_at, p.completed_at, p.created_at, " +
                        "se.name as source_env_name, te.name as target_env_name " +
                        "FROM environment_promotion p " +
                        "JOIN environment se ON se.id = p.source_env_id " +
                        "JOIN environment te ON te.id = p.target_env_id " +
                        "WHERE p.tenant_id = ? ORDER BY p.created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset
        );
    }

    public List<Map<String, Object>> findByEnvironment(String envId, String tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT p.id, p.tenant_id, p.source_env_id, p.target_env_id, p.status, " +
                        "p.promotion_type, p.items_promoted, p.items_skipped, p.items_failed, " +
                        "p.promoted_by, p.started_at, p.completed_at, p.created_at, " +
                        "se.name as source_env_name, te.name as target_env_name " +
                        "FROM environment_promotion p " +
                        "JOIN environment se ON se.id = p.source_env_id " +
                        "JOIN environment te ON te.id = p.target_env_id " +
                        "WHERE (p.source_env_id = ? OR p.target_env_id = ?) AND p.tenant_id = ? " +
                        "ORDER BY p.created_at DESC",
                envId, envId, tenantId
        );
    }

    public void updateStatus(String promotionId, String status) {
        jdbcTemplate.update(
                "UPDATE environment_promotion SET status = ?, updated_at = NOW() WHERE id = ?",
                status, promotionId
        );
    }

    public void markStarted(String promotionId) {
        jdbcTemplate.update(
                "UPDATE environment_promotion SET status = 'IN_PROGRESS', started_at = NOW(), " +
                        "updated_at = NOW() WHERE id = ?",
                promotionId
        );
    }

    public void markCompleted(String promotionId, int itemsPromoted, int itemsSkipped,
                              int itemsFailed, String changesSummary) {
        String status = itemsFailed > 0 ? "FAILED" : "COMPLETED";
        jdbcTemplate.update(
                "UPDATE environment_promotion SET status = ?, items_promoted = ?, items_skipped = ?, " +
                        "items_failed = ?, changes_summary = ?::jsonb, completed_at = NOW(), " +
                        "updated_at = NOW() WHERE id = ?",
                status, itemsPromoted, itemsSkipped, itemsFailed, changesSummary, promotionId
        );
    }

    public void markFailed(String promotionId, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE environment_promotion SET status = 'FAILED', error_message = ?, " +
                        "completed_at = NOW(), updated_at = NOW() WHERE id = ?",
                errorMessage, promotionId
        );
    }

    public void approve(String promotionId, String approvedBy) {
        jdbcTemplate.update(
                "UPDATE environment_promotion SET status = 'APPROVED', approved_by = ?, " +
                        "approved_at = NOW(), updated_at = NOW() WHERE id = ?",
                approvedBy, promotionId
        );
    }

    public void markRolledBack(String promotionId) {
        jdbcTemplate.update(
                "UPDATE environment_promotion SET status = 'ROLLED_BACK', updated_at = NOW() WHERE id = ?",
                promotionId
        );
    }

    // --- Promotion item methods ---

    public String createItem(String promotionId, String itemType, String itemId,
                             String itemName, String action) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO promotion_item (id, promotion_id, item_type, item_id, item_name, action, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'PENDING')",
                id, promotionId, itemType, itemId, itemName, action
        );
        return id;
    }

    public List<Map<String, Object>> findItemsByPromotion(String promotionId) {
        return jdbcTemplate.queryForList(
                "SELECT id, promotion_id, item_type, item_id, item_name, action, status, error_message " +
                        "FROM promotion_item WHERE promotion_id = ? ORDER BY item_type, item_name",
                promotionId
        );
    }

    public void updateItemStatus(String itemId, String status, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE promotion_item SET status = ?, error_message = ? WHERE id = ?",
                status, errorMessage, itemId
        );
    }
}
