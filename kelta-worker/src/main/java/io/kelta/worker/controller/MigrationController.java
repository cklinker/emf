package io.kelta.worker.controller;

import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.CollectionVersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schema-migration planner — the <em>non-destructive</em> half. Snapshots a collection's schema
 * into a version and builds a read-only plan diffing the current live schema against a stored
 * target version. Applying a plan (destructive DDL) is a separate, guarded endpoint (follow-up).
 *
 * <p>Lives on the {@code /api/migrations/**} static route and is gated in-controller on
 * {@code CUSTOMIZE_APPLICATION} (schema authoring), since {@code /api/*} routes only get the
 * blanket {@code API_ACCESS} gateway check.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/migrations")
public class MigrationController {

    private static final String SCHEMA_PERMISSION = "CUSTOMIZE_APPLICATION";

    private final CollectionVersionService versionService;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;

    public MigrationController(CollectionVersionService versionService,
                              CerbosPermissionResolver permissionResolver,
                              BootstrapRepository bootstrapRepository) {
        this.versionService = versionService;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
    }

    /** Captures the collection's current schema as a new version snapshot. */
    @PostMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> snapshot(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        requireSchemaPermission(request);
        String collectionId = asString(body.get("collectionId"));
        if (collectionId == null) {
            return ResponseEntity.badRequest().body(error("collectionId is required"));
        }
        try {
            int version = versionService.snapshot(collectionId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collectionId", collectionId);
            out.put("version", version);
            return ResponseEntity.status(HttpStatus.CREATED).body(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(e.getMessage()));
        }
    }

    /** Lists a collection's stored schema versions (newest first). */
    @GetMapping("/versions")
    public ResponseEntity<Map<String, Object>> versions(@RequestParam String collectionId,
                                                        HttpServletRequest request) {
        requireSchemaPermission(request);
        List<Map<String, Object>> versions = versionService.listVersions(collectionId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("collectionId", collectionId);
        out.put("versions", versions);
        return ResponseEntity.ok(out);
    }

    /** Builds a read-only migration plan from the current live schema to a stored target version. */
    @PostMapping("/plan")
    public ResponseEntity<Map<String, Object>> plan(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        requireSchemaPermission(request);
        String collectionId = asString(body.get("collectionId"));
        Integer targetVersion = body.get("targetVersion") instanceof Number n ? n.intValue() : null;
        if (collectionId == null || targetVersion == null) {
            return ResponseEntity.badRequest().body(error("collectionId and targetVersion are required"));
        }
        try {
            Optional<Map<String, Object>> plan = versionService.buildPlan(collectionId, targetVersion);
            return plan.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(error("No stored version " + targetVersion + " for collection " + collectionId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(e.getMessage()));
        }
    }

    // --- helpers ------------------------------------------------------------

    private void requireSchemaPermission(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);
        if (profileId == null || profileId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        boolean granted = bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> SCHEMA_PERMISSION.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
        if (!granted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SCHEMA_PERMISSION + " permission required");
        }
    }

    private static String asString(Object v) {
        return v instanceof String s && !s.isBlank() ? s : null;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }
}
