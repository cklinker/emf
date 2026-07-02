package io.kelta.worker.controller;

import io.kelta.runtime.query.InvalidQueryException;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.RecordMergeService;
import io.kelta.worker.service.RecordMergeService.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge write path for duplicate records: {@code POST /api/collections/{name}/merge} with
 * {@code {masterId, duplicateIds:[...], fieldOverrides?}} keeps the master, re-parents inbound
 * FKs off the duplicates, and deletes them. The read-only counterpart is
 * {@link DuplicateDetectionController}.
 *
 * <p>Under the already-routed {@code /api/collections/**}; gated in-controller on
 * {@code MANAGE_DATA} — the same cross-record data-management authority as detection. Because a
 * merge is a bulk delete + re-parent, it is deliberately <em>not</em> auto-applied by any flow;
 * an operator invokes it explicitly. The whole merge runs in one transaction so a mid-merge
 * failure rolls back. Each merge is written to the {@code security.audit} log.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/collections/{collectionName}/merge")
public class RecordMergeController {

    private static final String PERMISSION = "MANAGE_DATA";

    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private final RecordMergeService mergeService;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;

    public RecordMergeController(RecordMergeService mergeService,
                                 CerbosPermissionResolver permissionResolver,
                                 BootstrapRepository bootstrapRepository) {
        this.mergeService = mergeService;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
    }

    @PostMapping
    @Transactional
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> merge(
            @PathVariable String collectionName,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        requirePermission(request);

        String masterId = body.get("masterId") instanceof String s ? s : null;
        if (masterId == null || masterId.isBlank()) {
            return ResponseEntity.badRequest().body(error("masterId is required"));
        }

        List<String> duplicateIds = body.get("duplicateIds") instanceof List<?> l
                ? l.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        if (duplicateIds.isEmpty()) {
            return ResponseEntity.badRequest().body(error("duplicateIds must be a non-empty array"));
        }

        Map<String, Object> fieldOverrides = body.get("fieldOverrides") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        try {
            Result result = mergeService.merge(collectionName, masterId, duplicateIds, fieldOverrides);

            securityLog.info(
                    "security_event=RECORD_MERGE user={} tenant={} collection={} master={} "
                            + "duplicates={} reparented={} deleted={}",
                    userEmail, tenantId, collectionName, masterId,
                    duplicateIds, result.reparentedRecords(), result.deletedIds().size());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collectionName", collectionName);
            out.put("masterId", result.masterId());
            out.put("deletedIds", result.deletedIds());
            out.put("reparentedRecords", result.reparentedRecords());
            out.put("reparented", result.reparented().stream().map(r -> {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("collection", r.collection());
                rm.put("field", r.field());
                rm.put("count", r.count());
                return rm;
            }).toList());
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException | InvalidQueryException e) {
            // Unknown collection, missing record, or an invalid field → 400 (client error).
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    private void requirePermission(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);
        if (profileId == null || profileId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        boolean granted = bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> PERMISSION.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
        if (!granted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, PERMISSION + " permission required");
        }
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }
}
