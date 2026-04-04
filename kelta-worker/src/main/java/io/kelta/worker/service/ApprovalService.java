package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.ApprovalRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service implementing the approval workflow engine.
 *
 * <p>Handles submit-for-approval, approve, reject, and recall operations.
 * Manages approval instance lifecycle, step advancement, field updates,
 * and record locking during active approvals.
 *
 * @since 1.0.0
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository approvalRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ApprovalService(ApprovalRepository approvalRepository,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper) {
        this.approvalRepository = approvalRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Submits a record for approval.
     *
     * <p>Finds the matching approval process for the collection, creates an approval instance,
     * creates the first step instance(s), and applies on-submit field updates.
     *
     * @param collectionId the collection containing the record
     * @param recordId the record to submit for approval
     * @param submittedBy the user ID of the submitter
     * @param processId optional specific process ID (null = auto-detect)
     * @return the result containing the approval instance ID and status
     */
    @Transactional
    public ApprovalActionResult submitForApproval(String collectionId, String recordId,
                                                   String submittedBy, String processId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ApprovalActionResult.error("No tenant context");
        }

        // Check for existing pending approval
        var existing = approvalRepository.findPendingInstanceForRecord(collectionId, recordId, tenantId);
        if (existing.isPresent()) {
            return ApprovalActionResult.error("Record already has a pending approval");
        }

        // Find the approval process
        Map<String, Object> process;
        if (processId != null) {
            var processOpt = approvalRepository.findProcessById(processId, tenantId);
            if (processOpt.isEmpty()) {
                return ApprovalActionResult.error("Approval process not found: " + processId);
            }
            process = processOpt.get();
        } else {
            var processes = approvalRepository.findActiveProcessesForCollection(collectionId, tenantId);
            if (processes.isEmpty()) {
                return ApprovalActionResult.error("No active approval process found for this collection");
            }
            process = processes.getFirst();
        }

        String resolvedProcessId = (String) process.get("id");

        // Get the steps
        var steps = approvalRepository.findStepsByProcessId(resolvedProcessId);
        if (steps.isEmpty()) {
            return ApprovalActionResult.error("Approval process has no steps configured");
        }

        // Create the approval instance
        String instanceId = approvalRepository.createInstance(
                tenantId, resolvedProcessId, collectionId, recordId, submittedBy);

        // Create step instances for the first step
        var firstStep = steps.getFirst();
        createStepInstances(firstStep, instanceId, collectionId, recordId, tenantId);

        // Apply on-submit field updates
        applyFieldUpdates(process.get("on_submit_field_updates"), collectionId, recordId, tenantId);

        log.info("Approval submitted: instanceId={}, processId={}, recordId={}, submittedBy={}",
                instanceId, resolvedProcessId, recordId, submittedBy);

        return ApprovalActionResult.success(instanceId, "PENDING", "Approval submitted successfully");
    }

    /**
     * Approves an approval step instance.
     *
     * <p>If all required approvals for the current step are complete, advances to the
     * next step or marks the entire approval as approved.
     *
     * @param instanceId the approval instance ID
     * @param userId the approver's user ID
     * @param comments optional comments
     * @return the result of the approve action
     */
    @Transactional
    public ApprovalActionResult approve(String instanceId, String userId, String comments) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ApprovalActionResult.error("No tenant context");
        }

        var instanceOpt = approvalRepository.findInstanceById(instanceId, tenantId);
        if (instanceOpt.isEmpty()) {
            return ApprovalActionResult.error("Approval instance not found");
        }

        var instance = instanceOpt.get();
        if (!"PENDING".equals(instance.get("status"))) {
            return ApprovalActionResult.error("Approval is not pending (status: " + instance.get("status") + ")");
        }

        // Find this user's pending step instance
        var stepInstanceOpt = approvalRepository.findStepInstanceForApprover(instanceId, userId);
        if (stepInstanceOpt.isEmpty()) {
            return ApprovalActionResult.error("No pending approval step found for this user");
        }

        var stepInstance = stepInstanceOpt.get();
        String stepInstanceId = (String) stepInstance.get("id");
        String stepId = (String) stepInstance.get("step_id");
        boolean unanimityRequired = Boolean.TRUE.equals(stepInstance.get("unanimity_required"));
        String onApproveAction = (String) stepInstance.get("on_approve_action");

        // Update this step instance
        approvalRepository.updateStepInstanceStatus(stepInstanceId, "APPROVED", comments);

        log.info("Approval step approved: instanceId={}, stepInstanceId={}, userId={}",
                instanceId, stepInstanceId, userId);

        // Check if the step is complete (all approvers approved, or unanimity not required)
        if (unanimityRequired) {
            var pendingSteps = approvalRepository.findPendingStepInstances(instanceId, stepId);
            if (!pendingSteps.isEmpty()) {
                return ApprovalActionResult.success(instanceId, "PENDING",
                        "Approved. Waiting for remaining approvers.");
            }
        }

        // Step is complete — determine next action
        return handleStepCompletion(instanceId, instance, onApproveAction, "APPROVED", tenantId);
    }

    /**
     * Rejects an approval step instance.
     *
     * @param instanceId the approval instance ID
     * @param userId the rejector's user ID
     * @param comments optional comments (recommended for rejections)
     * @return the result of the reject action
     */
    @Transactional
    public ApprovalActionResult reject(String instanceId, String userId, String comments) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ApprovalActionResult.error("No tenant context");
        }

        var instanceOpt = approvalRepository.findInstanceById(instanceId, tenantId);
        if (instanceOpt.isEmpty()) {
            return ApprovalActionResult.error("Approval instance not found");
        }

        var instance = instanceOpt.get();
        if (!"PENDING".equals(instance.get("status"))) {
            return ApprovalActionResult.error("Approval is not pending (status: " + instance.get("status") + ")");
        }

        var stepInstanceOpt = approvalRepository.findStepInstanceForApprover(instanceId, userId);
        if (stepInstanceOpt.isEmpty()) {
            return ApprovalActionResult.error("No pending approval step found for this user");
        }

        var stepInstance = stepInstanceOpt.get();
        String stepInstanceId = (String) stepInstance.get("id");
        String onRejectAction = (String) stepInstance.get("on_reject_action");

        // Update this step instance
        approvalRepository.updateStepInstanceStatus(stepInstanceId, "REJECTED", comments);

        log.info("Approval step rejected: instanceId={}, stepInstanceId={}, userId={}",
                instanceId, stepInstanceId, userId);

        // Determine action on rejection
        return handleStepCompletion(instanceId, instance, onRejectAction, "REJECTED", tenantId);
    }

    /**
     * Recalls (withdraws) a pending approval. Only the submitter can recall.
     *
     * @param instanceId the approval instance ID
     * @param userId the user requesting the recall (must be the original submitter)
     * @return the result of the recall action
     */
    @Transactional
    public ApprovalActionResult recall(String instanceId, String userId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ApprovalActionResult.error("No tenant context");
        }

        var instanceOpt = approvalRepository.findInstanceById(instanceId, tenantId);
        if (instanceOpt.isEmpty()) {
            return ApprovalActionResult.error("Approval instance not found");
        }

        var instance = instanceOpt.get();
        if (!"PENDING".equals(instance.get("status"))) {
            return ApprovalActionResult.error("Approval is not pending (status: " + instance.get("status") + ")");
        }

        // Verify submitter
        String submittedBy = (String) instance.get("submitted_by");
        if (!userId.equals(submittedBy)) {
            return ApprovalActionResult.error("Only the submitter can recall an approval");
        }

        // Check if recall is allowed
        String processId = (String) instance.get("approval_process_id");
        var processOpt = approvalRepository.findProcessById(processId, tenantId);
        if (processOpt.isPresent()) {
            Boolean allowRecall = (Boolean) processOpt.get().get("allow_recall");
            if (Boolean.FALSE.equals(allowRecall)) {
                return ApprovalActionResult.error("Recall is not allowed for this approval process");
            }
        }

        // Cancel all pending step instances
        approvalRepository.cancelPendingStepInstances(instanceId);

        // Update instance status
        approvalRepository.updateInstanceStatus(instanceId, "RECALLED", null);

        // Apply on-recall field updates
        String collectionId = (String) instance.get("collection_id");
        String recordId = (String) instance.get("record_id");
        if (processOpt.isPresent()) {
            applyFieldUpdates(processOpt.get().get("on_recall_field_updates"),
                    collectionId, recordId, tenantId);
        }

        log.info("Approval recalled: instanceId={}, userId={}", instanceId, userId);

        return ApprovalActionResult.success(instanceId, "RECALLED", "Approval recalled successfully");
    }

    /**
     * Gets the approval history for a record.
     */
    public List<Map<String, Object>> getApprovalHistory(String collectionId, String recordId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return List.of();
        }

        var instances = approvalRepository.findInstancesForRecord(collectionId, recordId, tenantId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (var instance : instances) {
            var entry = new HashMap<>(instance);
            String instanceId = (String) instance.get("id");
            entry.put("steps", approvalRepository.findStepInstancesByInstanceId(instanceId));
            result.add(entry);
        }

        return result;
    }

    /**
     * Gets the current approval status for a record.
     */
    public Optional<Map<String, Object>> getCurrentApprovalStatus(String collectionId,
                                                                    String recordId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return Optional.empty();
        }

        var instanceOpt = approvalRepository.findPendingInstanceForRecord(
                collectionId, recordId, tenantId);
        if (instanceOpt.isEmpty()) {
            return Optional.empty();
        }

        var instance = instanceOpt.get();
        var result = new HashMap<>(instance);
        String instanceId = (String) instance.get("id");
        result.put("steps", approvalRepository.findStepInstancesByInstanceId(instanceId));

        return Optional.of(result);
    }

    /**
     * Checks whether a record is locked due to an active approval process.
     */
    public boolean isRecordLocked(String collectionId, String recordId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return false;
        }

        var editability = approvalRepository.getRecordEditability(collectionId, recordId, tenantId);
        return editability.isPresent() && "LOCKED".equals(editability.get());
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private ApprovalActionResult handleStepCompletion(String instanceId,
                                                       Map<String, Object> instance,
                                                       String action,
                                                       String actionType,
                                                       String tenantId) {
        String processId = (String) instance.get("approval_process_id");
        String collectionId = (String) instance.get("collection_id");
        String recordId = (String) instance.get("record_id");
        int currentStep = ((Number) instance.get("current_step_number")).intValue();

        if ("REJECT_FINAL".equals(action) || ("REJECTED".equals(actionType) && action == null)) {
            // Final rejection
            approvalRepository.cancelPendingStepInstances(instanceId);
            approvalRepository.updateInstanceStatus(instanceId, "REJECTED", null);

            var processOpt = approvalRepository.findProcessById(processId, tenantId);
            processOpt.ifPresent(p ->
                    applyFieldUpdates(p.get("on_rejection_field_updates"), collectionId, recordId, tenantId));

            log.info("Approval rejected (final): instanceId={}", instanceId);
            return ApprovalActionResult.success(instanceId, "REJECTED", "Approval rejected");
        }

        if ("NEXT_STEP".equals(action) || ("APPROVED".equals(actionType) && action == null)) {
            // Advance to next step
            var steps = approvalRepository.findStepsByProcessId(processId);
            var nextStepOpt = steps.stream()
                    .filter(s -> ((Number) s.get("step_number")).intValue() > currentStep)
                    .findFirst();

            if (nextStepOpt.isPresent()) {
                var nextStep = nextStepOpt.get();
                int nextStepNumber = ((Number) nextStep.get("step_number")).intValue();
                approvalRepository.advanceStep(instanceId, nextStepNumber);
                createStepInstances(nextStep, instanceId, collectionId, recordId, tenantId);

                log.info("Approval advanced to step {}: instanceId={}", nextStepNumber, instanceId);
                return ApprovalActionResult.success(instanceId, "PENDING",
                        "Advanced to step " + nextStepNumber);
            }

            // No more steps — approval complete
            approvalRepository.updateInstanceStatus(instanceId, "APPROVED", null);

            var processOpt = approvalRepository.findProcessById(processId, tenantId);
            processOpt.ifPresent(p ->
                    applyFieldUpdates(p.get("on_approval_field_updates"), collectionId, recordId, tenantId));

            log.info("Approval completed (approved): instanceId={}", instanceId);
            return ApprovalActionResult.success(instanceId, "APPROVED", "Approval completed");
        }

        // Unknown action — treat as rejection
        log.warn("Unknown step action '{}' for instanceId={}, treating as rejection", action, instanceId);
        approvalRepository.updateInstanceStatus(instanceId, "REJECTED", null);
        return ApprovalActionResult.success(instanceId, "REJECTED", "Approval rejected (unknown action)");
    }

    private void createStepInstances(Map<String, Object> step, String instanceId,
                                      String collectionId, String recordId, String tenantId) {
        String stepId = (String) step.get("id");
        String approverType = (String) step.get("approver_type");
        String approverId = (String) step.get("approver_id");
        String approverField = (String) step.get("approver_field");

        switch (approverType) {
            case "USER" -> {
                if (approverId != null) {
                    approvalRepository.createStepInstance(instanceId, stepId, approverId);
                }
            }
            case "FIELD" -> {
                // Resolve approver from a field on the record
                String resolvedUserId = resolveApproverFromField(collectionId, recordId,
                        approverField, tenantId);
                if (resolvedUserId != null) {
                    approvalRepository.createStepInstance(instanceId, stepId, resolvedUserId);
                } else {
                    log.warn("Could not resolve approver from field '{}' for record {}",
                            approverField, recordId);
                }
            }
            case "QUEUE" -> {
                // Queue-based approval — assign to the queue owner/first available
                if (approverId != null) {
                    approvalRepository.createStepInstance(instanceId, stepId, approverId);
                }
            }
            default -> log.warn("Unknown approver type: {}", approverType);
        }
    }

    private String resolveApproverFromField(String collectionId, String recordId,
                                             String fieldName, String tenantId) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }

        try {
            // Look up the collection table name
            var collResults = jdbcTemplate.queryForList(
                    "SELECT table_name FROM collection WHERE id = ? AND tenant_id = ?",
                    collectionId, tenantId);

            if (collResults.isEmpty()) {
                return null;
            }

            String tableName = (String) collResults.getFirst().get("table_name");

            // Convert camelCase field name to snake_case column name
            String columnName = fieldName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();

            var results = jdbcTemplate.queryForList(
                    "SELECT " + columnName + " FROM " + tableName +
                            " WHERE id = ? AND tenant_id = ?",
                    recordId, tenantId);

            if (!results.isEmpty()) {
                Object value = results.getFirst().get(columnName);
                return value != null ? value.toString() : null;
            }
        } catch (Exception e) {
            log.error("Failed to resolve approver from field '{}': {}", fieldName, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void applyFieldUpdates(Object fieldUpdatesObj, String collectionId,
                                    String recordId, String tenantId) {
        if (fieldUpdatesObj == null) {
            return;
        }

        try {
            List<Map<String, Object>> updates;
            if (fieldUpdatesObj instanceof String str) {
                if (str.isBlank() || "[]".equals(str.trim())) {
                    return;
                }
                updates = objectMapper.readValue(str, new TypeReference<>() {});
            } else if (fieldUpdatesObj instanceof List) {
                updates = (List<Map<String, Object>>) fieldUpdatesObj;
            } else {
                return;
            }

            if (updates.isEmpty()) {
                return;
            }

            // Look up the collection table name
            var collResults = jdbcTemplate.queryForList(
                    "SELECT table_name FROM collection WHERE id = ? AND tenant_id = ?",
                    collectionId, tenantId);

            if (collResults.isEmpty()) {
                log.warn("Collection not found for field updates: {}", collectionId);
                return;
            }

            String tableName = (String) collResults.getFirst().get("table_name");

            for (Map<String, Object> update : updates) {
                String field = (String) update.get("field");
                Object value = update.get("value");

                if (field == null || field.isBlank()) {
                    continue;
                }

                String columnName = field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();

                try {
                    jdbcTemplate.update(
                            "UPDATE " + tableName + " SET " + columnName + " = ?, updated_at = NOW() " +
                                    "WHERE id = ? AND tenant_id = ?",
                            value, recordId, tenantId);

                    log.debug("Applied field update: {}={} on record {}", field, value, recordId);
                } catch (Exception e) {
                    log.error("Failed to apply field update {}={}: {}", field, value, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse/apply field updates: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Result type
    // =========================================================================

    /**
     * Result of an approval action.
     */
    public record ApprovalActionResult(
            boolean success,
            String instanceId,
            String status,
            String message
    ) {
        public static ApprovalActionResult success(String instanceId, String status, String message) {
            return new ApprovalActionResult(true, instanceId, status, message);
        }

        public static ApprovalActionResult error(String message) {
            return new ApprovalActionResult(false, null, null, message);
        }

        public Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("success", success);
            if (instanceId != null) map.put("instanceId", instanceId);
            if (status != null) map.put("status", status);
            map.put("message", message);
            return map;
        }
    }
}
