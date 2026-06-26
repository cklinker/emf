package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.dependency.MetadataNode;
import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.service.MetadataDependencyService;
import io.kelta.worker.service.MetadataDependencyService.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Computes a dependency-ordered deployment plan for a change set.
     *
     * <p>Body: {@code { "changeSet": [ { "type": "COLLECTION", "id": "orders" }, ... ],
     * "includeDependencies": false }}. Returns the apply order (dependencies first), plus any
     * {@code missing} (unknown) and {@code cyclic} items.
     */
    @PostMapping("/deployment-plan")
    public ResponseEntity<Map<String, Object>> deploymentPlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {

        Object rawChangeSet = body == null ? null : body.get("changeSet");
        if (!(rawChangeSet instanceof List<?> items) || items.isEmpty()) {
            return ResponseEntity.badRequest().body(JsonApiResponseBuilder.error(
                    "400", "INVALID_CHANGESET", "Invalid change set",
                    "'changeSet' must be a non-empty array of { type, id } objects"));
        }

        List<MetadataNode> changeSet = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> entry)) {
                return ResponseEntity.badRequest().body(JsonApiResponseBuilder.error(
                        "400", "INVALID_CHANGESET", "Invalid change set",
                        "Each change-set entry must be an object with 'type' and 'id'"));
            }
            Object typeRaw = entry.get("type");
            Object idRaw = entry.get("id");
            if (typeRaw == null || idRaw == null) {
                return ResponseEntity.badRequest().body(JsonApiResponseBuilder.error(
                        "400", "INVALID_CHANGESET", "Invalid change set",
                        "Each change-set entry requires 'type' and 'id'"));
            }
            MetadataType metadataType;
            try {
                metadataType = MetadataType.valueOf(String.valueOf(typeRaw).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(JsonApiResponseBuilder.error(
                        "400", "INVALID_TYPE", "Invalid metadata type",
                        "Unknown metadata type '" + typeRaw + "'"));
            }
            changeSet.add(MetadataNode.of(metadataType, String.valueOf(idRaw), null));
        }

        boolean includeDependencies = Boolean.TRUE.equals(body.get("includeDependencies"));
        Map<String, Object> result = dependencyService.deploymentPlan(tenantId, changeSet, includeDependencies);
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
