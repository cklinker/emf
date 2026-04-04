package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.ApprovalService;
import io.kelta.worker.service.ApprovalService.ApprovalActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for approval workflow operations.
 *
 * <p>Provides endpoints for submitting records for approval, approving, rejecting,
 * and recalling approvals, as well as querying approval status and history.
 *
 * <p>CRUD operations on approval-processes, approval-steps, approval-instances, and
 * approval-step-instances are handled by the DynamicCollectionRouter (they are system
 * collections). This controller adds action endpoints not covered by standard CRUD.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * Submits a record for approval.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "collectionId": "...",
     *   "recordId": "...",
     *   "processId": "..." (optional, auto-detects if omitted)
     * }
     * </pre>
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitForApproval(@RequestBody Map<String, Object> body,
                                                                  @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        String collectionId = (String) body.get("collectionId");
        String recordId = (String) body.get("recordId");
        String processId = (String) body.get("processId");

        if (collectionId == null || recordId == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "collectionId and recordId are required"));
        }

        if (userId == null) {
            userId = (String) body.get("submittedBy");
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User ID is required"));
        }

        ApprovalActionResult result = approvalService.submitForApproval(
                collectionId, recordId, userId, processId);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.toMap());
        }

        return ResponseEntity.ok(result.toMap());
    }

    /**
     * Approves a pending approval step.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "comments": "..." (optional)
     * }
     * </pre>
     */
    @PostMapping("/{instanceId}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String instanceId,
                                                        @RequestBody(required = false) Map<String, Object> body,
                                                        @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        if (userId == null && body != null) {
            userId = (String) body.get("userId");
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User ID is required"));
        }

        String comments = body != null ? (String) body.get("comments") : null;

        ApprovalActionResult result = approvalService.approve(instanceId, userId, comments);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.toMap());
        }

        return ResponseEntity.ok(result.toMap());
    }

    /**
     * Rejects a pending approval step.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "comments": "..." (recommended)
     * }
     * </pre>
     */
    @PostMapping("/{instanceId}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable String instanceId,
                                                       @RequestBody(required = false) Map<String, Object> body,
                                                       @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        if (userId == null && body != null) {
            userId = (String) body.get("userId");
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User ID is required"));
        }

        String comments = body != null ? (String) body.get("comments") : null;

        ApprovalActionResult result = approvalService.reject(instanceId, userId, comments);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.toMap());
        }

        return ResponseEntity.ok(result.toMap());
    }

    /**
     * Recalls (withdraws) a pending approval. Only the original submitter can recall.
     */
    @PostMapping("/{instanceId}/recall")
    public ResponseEntity<Map<String, Object>> recall(@PathVariable String instanceId,
                                                       @RequestHeader(value = "X-User-Id", required = false) String userId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        if (userId == null && body != null) {
            userId = (String) body.get("userId");
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User ID is required"));
        }

        ApprovalActionResult result = approvalService.recall(instanceId, userId);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.toMap());
        }

        return ResponseEntity.ok(result.toMap());
    }

    /**
     * Gets the current approval status for a record.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam String collectionId,
                                                          @RequestParam String recordId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        var statusOpt = approvalService.getCurrentApprovalStatus(collectionId, recordId);
        if (statusOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasActiveApproval", false));
        }

        var result = statusOpt.get();
        result.put("hasActiveApproval", true);
        return ResponseEntity.ok(result);
    }

    /**
     * Gets the approval history for a record.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(@RequestParam String collectionId,
                                                                 @RequestParam String recordId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(null);
        }

        var history = approvalService.getApprovalHistory(collectionId, recordId);
        return ResponseEntity.ok(history);
    }

    /**
     * Checks if a record is locked due to an active approval.
     */
    @GetMapping("/lock-status")
    public ResponseEntity<Map<String, Object>> getLockStatus(@RequestParam String collectionId,
                                                              @RequestParam String recordId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        boolean locked = approvalService.isRecordLocked(collectionId, recordId);
        return ResponseEntity.ok(Map.of("locked", locked, "collectionId", collectionId, "recordId", recordId));
    }
}
