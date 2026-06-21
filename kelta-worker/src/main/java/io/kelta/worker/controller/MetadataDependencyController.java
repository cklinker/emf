package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.service.MetadataDependencyService;
import io.kelta.worker.service.MetadataDependencyService.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only metadata dependency / impact-analysis endpoints.
 *
 * <p>Responses use a {@code { "data": { ... } }} envelope whose body has no {@code attributes}
 * or {@code relationships} keys, so the read-side field-security advice treats it as a non-record
 * payload and leaves it untouched (these are configuration-graph results, not tenant records).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/metadata")
public class MetadataDependencyController {

    private final MetadataDependencyService dependencyService;

    public MetadataDependencyController(MetadataDependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    /**
     * Impact analysis for a single metadata node.
     *
     * @param type      metadata type (COLLECTION, FIELD, FLOW, ...)
     * @param id        the metadata object's id
     * @param direction {@code dependents} (default — what breaks if it changes) or
     *                  {@code dependencies} (what it relies on)
     */
    @GetMapping("/impact")
    public ResponseEntity<Map<String, Object>> impact(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam("type") String type,
            @RequestParam("id") String id,
            @RequestParam(value = "direction", defaultValue = "dependents") String direction) {

        MetadataType metadataType;
        try {
            metadataType = MetadataType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(JsonApiResponseBuilder.error(
                    "400", "INVALID_TYPE", "Invalid metadata type",
                    "Unknown metadata type '" + type + "'"));
        }

        Direction dir = "dependencies".equalsIgnoreCase(direction.trim())
                ? Direction.DEPENDENCIES : Direction.DEPENDENTS;

        Map<String, Object> result = dependencyService.impact(tenantId, metadataType, id, dir);
        return ResponseEntity.ok(wrap(result));
    }

    /**
     * The full dependency graph (nodes, edges, detected cycles) for the tenant.
     */
    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> graph(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(wrap(dependencyService.graphSummary(tenantId)));
    }

    private static Map<String, Object> wrap(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        return body;
    }
}
