package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.service.ApprovalService;
import io.kelta.worker.service.ApprovalService.ApprovalActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final UserIdResolver userIdResolver;

    public ApprovalController(ApprovalService approvalService, UserIdResolver userIdResolver) {
        this.approvalService = approvalService;
        this.userIdResolver = userIdResolver;
    }

    /**
     * Resolves the acting user for an approval write from the gateway-stamped
     * {@code X-User-Id} header ONLY — body-supplied identity ({@code userId}/{@code submittedBy})
     * is never trusted (any caller could act as any assignee). The gateway strips the header
     * from client requests and re-stamps it from the validated principal, so its value is
     * trustworthy but is an email identifier; {@code assigned_to}/{@code submitted_by} store
     * platform_user UUIDs, so it must resolve through {@link UserIdResolver}. Fail-closed:
     * a missing header or an identifier that does not resolve to a UUID is rejected with 403
     * (the resolver returns its input unchanged on failure, hence the UUID-shape check).
     */
    private String resolveActingUser(String headerUserId) {
        if (headerUserId == null || headerUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        String resolved = userIdResolver.resolve(headerUserId, TenantContext.get());
        try {
            UUID.fromString(resolved);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unresolvable user identity");
        }
        return resolved;
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
     *
     * <p>The submitter is always the authenticated caller (gateway-stamped {@code X-User-Id});
     * a body {@code submittedBy} is accepted on the wire for back-compat but ignored.
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

        String actingUser = resolveActingUser(userId);

        ApprovalActionResult result = approvalService.submitForApproval(
                collectionId, recordId, actingUser, processId);

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

        String actingUser = resolveActingUser(userId);
        String comments = body != null ? (String) body.get("comments") : null;

        ApprovalActionResult result = approvalService.approve(instanceId, actingUser, comments);

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

        String actingUser = resolveActingUser(userId);
        String comments = body != null ? (String) body.get("comments") : null;

        ApprovalActionResult result = approvalService.reject(instanceId, actingUser, comments);

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

        String actingUser = resolveActingUser(userId);

        ApprovalActionResult result = approvalService.recall(instanceId, actingUser);

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
