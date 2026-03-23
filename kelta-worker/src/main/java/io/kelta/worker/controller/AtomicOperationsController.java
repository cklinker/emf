package io.kelta.worker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.jsonapi.AtomicOperation;
import io.kelta.jsonapi.AtomicResult;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.router.AtomicOperationExecutor;
import io.kelta.runtime.router.AtomicOperationExecutor.AtomicOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * JSON:API Atomic Operations endpoint.
 *
 * <p>Accepts a batch of create/update/delete operations and executes them
 * within a single database transaction. All operations succeed or all fail.
 *
 * @see <a href="https://jsonapi.org/ext/atomic">JSON:API Atomic Operations</a>
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/operations")
public class AtomicOperationsController {

    private static final Logger log = LoggerFactory.getLogger(AtomicOperationsController.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final int HARD_CEILING = 500;

    private final AtomicOperationExecutor executor;
    private final int maxOperations;

    public AtomicOperationsController(
            QueryEngine queryEngine,
            CollectionRegistry collectionRegistry,
            @Value("${kelta.api.max-batch-operations:100}") int maxOperations) {
        this.executor = new AtomicOperationExecutor(queryEngine, collectionRegistry);
        this.maxOperations = Math.min(maxOperations, HARD_CEILING);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> executeOperations(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();

        // Parse operations from request body
        Object opsObj = body.get("atomic:operations");
        if (opsObj == null) {
            return ResponseEntity.badRequest().body(errorResponse(
                    "400", "Invalid request", "Missing 'atomic:operations' array", null));
        }

        List<AtomicOperation> operations;
        try {
            operations = parseOperations(opsObj);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorResponse(
                    "400", "Parse error", e.getMessage(), null));
        }

        // Validate operation count
        if (operations.isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse(
                    "400", "Empty batch", "No operations provided", null));
        }

        if (operations.size() > maxOperations) {
            return ResponseEntity.unprocessableEntity().body(errorResponse(
                    "422", "Too many operations",
                    "Request contains " + operations.size() + " operations, maximum is " + maxOperations,
                    null));
        }

        // Execute within transaction
        try {
            List<AtomicResult> results = executor.execute(operations);

            log.info("Atomic operations completed: {} operations for tenant {}",
                    operations.size(), tenantId);
            securityLog.info("security_event=ATOMIC_OPS_SUCCESS tenant={} count={}",
                    tenantId, operations.size());

            return ResponseEntity.ok(Map.of("atomic:results", results));
        } catch (AtomicOperationException e) {
            log.warn("Atomic operation {} failed at index {}: {}",
                    e.getOperationType(), e.getOperationIndex(), e.getMessage());

            return ResponseEntity.unprocessableEntity().body(errorResponse(
                    "422", "Operation failed", e.getMessage(),
                    "/atomic:operations/" + e.getOperationIndex()));
        } catch (Exception e) {
            log.error("Unexpected error during atomic operations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse(
                    "500", "Internal error", "An unexpected error occurred", null));
        }
    }

    @SuppressWarnings("unchecked")
    private List<AtomicOperation> parseOperations(Object opsObj) {
        if (!(opsObj instanceof List<?> opsList)) {
            throw new IllegalArgumentException("'atomic:operations' must be an array");
        }

        ObjectMapper mapper = new ObjectMapper();
        return opsList.stream()
                .map(op -> mapper.convertValue(op, AtomicOperation.class))
                .toList();
    }

    private Map<String, Object> errorResponse(String status, String title, String detail, String pointer) {
        var error = new java.util.LinkedHashMap<String, Object>();
        error.put("status", status);
        error.put("title", title);
        error.put("detail", detail);
        if (pointer != null) {
            error.put("source", Map.of("pointer", pointer));
        }
        return Map.of("errors", List.of(error));
    }
}
