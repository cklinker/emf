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
 * Repository for approval workflow persistence.
 *
 * <p>Handles CRUD operations on the approval_process, approval_step,
 * approval_instance, and approval_step_instance tables with tenant isolation.
 *
 * @since 1.0.0
 */
@Repository
public class ApprovalRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApprovalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================================================================
    // Approval Process queries
    // =========================================================================

    /**
     * Finds all active approval processes for a collection within a tenant.
     */
    public List<Map<String, Object>> findActiveProcessesForCollection(String collectionId, String tenantId) {
        return jdbcTemplate.queryForList(
                """
                SELECT ap.id, ap.name, ap.entry_criteria, ap.record_editability,
                       ap.initial_submitter_field, ap.on_submit_field_updates,
                       ap.on_approval_field_updates, ap.on_rejection_field_updates,
                       ap.on_recall_field_updates, ap.allow_recall, ap.execution_order
                FROM approval_process ap
                WHERE ap.collection_id = ? AND ap.tenant_id = ? AND ap.active = true
                ORDER BY ap.execution_order
                """,
                collectionId, tenantId
        );
    }

    /**
     * Finds an approval process by ID with tenant isolation.
     */
    public Optional<Map<String, Object>> findProcessById(String processId, String tenantId) {
        var results = jdbcTemplate.queryForList(
                """
                SELECT id, tenant_id, collection_id, name, entry_criteria,
                       record_editability, initial_submitter_field,
                       on_submit_field_updates, on_approval_field_updates,
                       on_rejection_field_updates, on_recall_field_updates,
                       allow_recall, active, execution_order
                FROM approval_process
                WHERE id = ? AND tenant_id = ?
                """,
                processId, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    // =========================================================================
    // Approval Step queries
    // =========================================================================

    /**
     * Finds all steps for an approval process, ordered by step number.
     */
    public List<Map<String, Object>> findStepsByProcessId(String processId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, approval_process_id, step_number, name, description,
                       entry_criteria, approver_type, approver_id, approver_field,
                       unanimity_required, escalation_timeout_hours, escalation_action,
                       on_approve_action, on_reject_action
                FROM approval_step
                WHERE approval_process_id = ?
                ORDER BY step_number
                """,
                processId
        );
    }

    /**
     * Finds a specific step by process ID and step number.
     */
    public Optional<Map<String, Object>> findStepByNumber(String processId, int stepNumber) {
        var results = jdbcTemplate.queryForList(
                """
                SELECT id, approval_process_id, step_number, name, description,
                       entry_criteria, approver_type, approver_id, approver_field,
                       unanimity_required, escalation_timeout_hours, escalation_action,
                       on_approve_action, on_reject_action
                FROM approval_step
                WHERE approval_process_id = ? AND step_number = ?
                """,
                processId, stepNumber
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    // =========================================================================
    // Approval Instance queries
    // =========================================================================

    /**
     * Creates a new approval instance.
     */
    public String createInstance(String tenantId, String processId, String collectionId,
                                 String recordId, String submittedBy) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO approval_instance
                    (id, tenant_id, approval_process_id, collection_id, record_id,
                     submitted_by, current_step_number, status, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, 'PENDING', NOW())
                """,
                id, tenantId, processId, collectionId, recordId, submittedBy
        );
        return id;
    }

    /**
     * Finds an approval instance by ID with tenant isolation.
     */
    public Optional<Map<String, Object>> findInstanceById(String instanceId, String tenantId) {
        var results = jdbcTemplate.queryForList(
                """
                SELECT id, tenant_id, approval_process_id, collection_id, record_id,
                       submitted_by, current_step_number, status, submitted_at, completed_at
                FROM approval_instance
                WHERE id = ? AND tenant_id = ?
                """,
                instanceId, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Finds the active (PENDING) approval instance for a record.
     */
    public Optional<Map<String, Object>> findPendingInstanceForRecord(String collectionId,
                                                                       String recordId,
                                                                       String tenantId) {
        var results = jdbcTemplate.queryForList(
                """
                SELECT id, tenant_id, approval_process_id, collection_id, record_id,
                       submitted_by, current_step_number, status, submitted_at
                FROM approval_instance
                WHERE collection_id = ? AND record_id = ? AND tenant_id = ? AND status = 'PENDING'
                ORDER BY submitted_at DESC
                LIMIT 1
                """,
                collectionId, recordId, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Updates the approval instance status and optionally advances the step.
     */
    public int updateInstanceStatus(String instanceId, String status, Integer currentStepNumber) {
        if (currentStepNumber != null) {
            return jdbcTemplate.update(
                    """
                    UPDATE approval_instance
                    SET status = ?, current_step_number = ?, completed_at = CASE WHEN ? IN ('APPROVED','REJECTED','RECALLED') THEN NOW() ELSE completed_at END
                    WHERE id = ?
                    """,
                    status, currentStepNumber, status, instanceId
            );
        }
        return jdbcTemplate.update(
                """
                UPDATE approval_instance
                SET status = ?, completed_at = CASE WHEN ? IN ('APPROVED','REJECTED','RECALLED') THEN NOW() ELSE completed_at END
                WHERE id = ?
                """,
                status, status, instanceId
        );
    }

    /**
     * Advances the current step number for an approval instance.
     */
    public int advanceStep(String instanceId, int nextStepNumber) {
        return jdbcTemplate.update(
                "UPDATE approval_instance SET current_step_number = ? WHERE id = ? AND status = 'PENDING'",
                nextStepNumber, instanceId
        );
    }

    /**
     * Finds all approval instances for a record (history).
     */
    public List<Map<String, Object>> findInstancesForRecord(String collectionId, String recordId,
                                                             String tenantId) {
        return jdbcTemplate.queryForList(
                """
                SELECT ai.id, ai.approval_process_id, ai.status, ai.submitted_by,
                       ai.current_step_number, ai.submitted_at, ai.completed_at,
                       ap.name as process_name
                FROM approval_instance ai
                JOIN approval_process ap ON ap.id = ai.approval_process_id
                WHERE ai.collection_id = ? AND ai.record_id = ? AND ai.tenant_id = ?
                ORDER BY ai.submitted_at DESC
                """,
                collectionId, recordId, tenantId
        );
    }

    // =========================================================================
    // Approval Step Instance queries
    // =========================================================================

    /**
     * Creates a new approval step instance (assigns an approver to a step).
     */
    public String createStepInstance(String instanceId, String stepId, String assignedTo) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO approval_step_instance
                    (id, approval_instance_id, step_id, assigned_to, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """,
                id, instanceId, stepId, assignedTo
        );
        return id;
    }

    /**
     * Finds all step instances for an approval instance.
     */
    public List<Map<String, Object>> findStepInstancesByInstanceId(String instanceId) {
        return jdbcTemplate.queryForList(
                """
                SELECT asi.id, asi.approval_instance_id, asi.step_id, asi.assigned_to,
                       asi.status, asi.comments, asi.acted_at,
                       ast.step_number, ast.name as step_name
                FROM approval_step_instance asi
                JOIN approval_step ast ON ast.id = asi.step_id
                ORDER BY ast.step_number, asi.id
                """.replace("ORDER BY", "WHERE asi.approval_instance_id = ? ORDER BY"),
                instanceId
        );
    }

    /**
     * Finds pending step instances for an approval instance at the current step.
     */
    public List<Map<String, Object>> findPendingStepInstances(String instanceId, String stepId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, approval_instance_id, step_id, assigned_to, status
                FROM approval_step_instance
                WHERE approval_instance_id = ? AND step_id = ? AND status = 'PENDING'
                """,
                instanceId, stepId
        );
    }

    /**
     * Updates a step instance status (approve/reject).
     */
    public int updateStepInstanceStatus(String stepInstanceId, String status, String comments) {
        return jdbcTemplate.update(
                """
                UPDATE approval_step_instance
                SET status = ?, comments = ?, acted_at = NOW()
                WHERE id = ?
                """,
                status, comments, stepInstanceId
        );
    }

    /**
     * Finds a specific step instance assigned to a user for a given approval instance.
     */
    public Optional<Map<String, Object>> findStepInstanceForApprover(String instanceId,
                                                                      String userId) {
        var results = jdbcTemplate.queryForList(
                """
                SELECT asi.id, asi.step_id, asi.status, asi.assigned_to,
                       ast.step_number, ast.on_approve_action, ast.on_reject_action,
                       ast.unanimity_required
                FROM approval_step_instance asi
                JOIN approval_step ast ON ast.id = asi.step_id
                JOIN approval_instance ai ON ai.id = asi.approval_instance_id
                WHERE asi.approval_instance_id = ? AND asi.assigned_to = ? AND asi.status = 'PENDING'
                AND ast.step_number = ai.current_step_number
                """,
                instanceId, userId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Cancels all pending step instances for an approval (used during recall).
     */
    public int cancelPendingStepInstances(String instanceId) {
        return jdbcTemplate.update(
                """
                UPDATE approval_step_instance
                SET status = 'REASSIGNED', acted_at = NOW()
                WHERE approval_instance_id = ? AND status = 'PENDING'
                """,
                instanceId
        );
    }

    /**
     * Checks if a record has a pending approval (used for record locking).
     */
    public boolean hasActiveApproval(String collectionId, String recordId, String tenantId) {
        var count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM approval_instance
                WHERE collection_id = ? AND record_id = ? AND tenant_id = ? AND status = 'PENDING'
                """,
                Integer.class,
                collectionId, recordId, tenantId
        );
        return count != null && count > 0;
    }

    /**
     * Gets the record editability mode for the active approval on a record.
     */
    public Optional<String> getRecordEditability(String collectionId, String recordId,
                                                  String tenantId) {
        var results = jdbcTemplate.queryForList(
                """
                SELECT ap.record_editability
                FROM approval_instance ai
                JOIN approval_process ap ON ap.id = ai.approval_process_id
                WHERE ai.collection_id = ? AND ai.record_id = ? AND ai.tenant_id = ?
                AND ai.status = 'PENDING'
                LIMIT 1
                """,
                collectionId, recordId, tenantId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) results.getFirst().get("record_editability"));
    }
}
