package com.emf.controlplane.controller;

import com.emf.controlplane.dto.BeforeSaveRequest;
import com.emf.controlplane.dto.BeforeSaveResponse;
import com.emf.controlplane.service.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Internal API for workflow evaluation during record save operations.
 * <p>
 * Called synchronously by the worker during record create/update to evaluate
 * BEFORE_CREATE and BEFORE_UPDATE workflow rules. Returns field updates to
 * apply before persisting the record.
 * <p>
 * No authentication required — only exposed on the internal network.
 * The {@code /internal/**} path is configured as {@code permitAll()} in SecurityConfig.
 */
@RestController
@RequestMapping("/internal/workflow")
public class InternalWorkflowController {

    private static final Logger log = LoggerFactory.getLogger(InternalWorkflowController.class);

    private final WorkflowEngine workflowEngine;

    public InternalWorkflowController(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    /**
     * Evaluates before-save workflow rules for a record being created or updated.
     * <p>
     * Only FIELD_UPDATE actions are supported for before-save triggers.
     * Returns accumulated field updates to apply before persist.
     *
     * @param request the before-save evaluation request
     * @return field updates, rules evaluated count, and actions executed count
     */
    @PostMapping("/before-save")
    public ResponseEntity<BeforeSaveResponse> evaluateBeforeSave(@RequestBody BeforeSaveRequest request) {
        log.debug("Before-save workflow evaluation: tenant={}, collection={}, changeType={}",
            request.getTenantId(), request.getCollectionName(), request.getChangeType());

        Map<String, Object> result = workflowEngine.evaluateBeforeSave(
            request.getTenantId(),
            request.getCollectionId(),
            request.getCollectionName(),
            request.getRecordId(),
            request.getData(),
            request.getPreviousData(),
            request.getChangedFields() != null ? request.getChangedFields() : List.of(),
            request.getUserId(),
            request.getChangeType()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
        int rulesEvaluated = (int) result.get("rulesEvaluated");
        int actionsExecuted = (int) result.get("actionsExecuted");

        // Check for lifecycle handler errors
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) result.get("errors");

        BeforeSaveResponse response = new BeforeSaveResponse(
                fieldUpdates, rulesEvaluated, actionsExecuted, errors);

        if (response.hasErrors()) {
            log.warn("Before-save evaluation returned validation errors for collection '{}': {}",
                    request.getCollectionName(), errors);
            return ResponseEntity.unprocessableEntity().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
