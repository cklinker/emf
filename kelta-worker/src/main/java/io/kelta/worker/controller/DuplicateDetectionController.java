package io.kelta.worker.controller;

import io.kelta.runtime.query.InvalidQueryException;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.DuplicateDetectionService;
import io.kelta.worker.service.DuplicateDetectionService.Result;
import io.kelta.worker.service.ReportExecutionService.MaskingPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only duplicate detection for a collection: {@code POST /api/collections/{name}/duplicates}
 * with {@code {matchFields:[...], limit?}} returns groups of records sharing the same match-field
 * values. Delegates to {@link DuplicateDetectionService} (authorized query path, no raw SQL).
 *
 * <p>Under the already-routed {@code /api/collections/**}; gated in-controller on
 * {@code MANAGE_DATA} (a cross-record data-management view).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/collections/{collectionName}/duplicates")
public class DuplicateDetectionController {

    private static final String PERMISSION = "MANAGE_DATA";

    private final DuplicateDetectionService detectionService;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;

    public DuplicateDetectionController(DuplicateDetectionService detectionService,
                                        CerbosPermissionResolver permissionResolver,
                                        BootstrapRepository bootstrapRepository) {
        this.detectionService = detectionService;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> findDuplicates(
            @PathVariable String collectionName,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        requirePermission(request);

        List<String> matchFields = body.get("matchFields") instanceof List<?> l
                ? l.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        if (matchFields.isEmpty()) {
            return ResponseEntity.badRequest().body(error("matchFields must be a non-empty array"));
        }
        int limit = body.get("limit") instanceof Number n ? n.intValue() : 100;

        try {
            Result result = detectionService.findDuplicates(collectionName, matchFields, limit,
                    principalOf(request));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collectionName", collectionName);
            out.put("matchFields", matchFields);
            out.put("scanned", result.scanned());
            out.put("truncated", result.truncated());
            out.put("groups", result.groups().stream().map(g -> {
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("values", g.values());
                gm.put("count", g.count());
                gm.put("recordIds", g.recordIds());
                return gm;
            }).toList());
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException | InvalidQueryException e) {
            // Unknown collection or an invalid match field → 400 (client error).
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    /** Builds the data-masking principal for the calling user from the gateway-forwarded identity headers. */
    private MaskingPrincipal principalOf(HttpServletRequest request) {
        return new MaskingPrincipal(
            permissionResolver.getEmail(request),
            permissionResolver.getProfileId(request),
            permissionResolver.getTenantId(request));
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
