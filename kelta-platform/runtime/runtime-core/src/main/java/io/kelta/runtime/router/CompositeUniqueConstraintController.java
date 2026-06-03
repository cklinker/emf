package io.kelta.runtime.router;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.CompositeUniqueConstraint;
import io.kelta.runtime.storage.PhysicalTableStorageAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoint for managing composite UNIQUE constraints on a collection's
 * physical table. Single-column unique constraints are still expressed via
 * {@code field.unique=true} when the field is created; this controller covers
 * the multi-column case that has no representation in the field model.
 *
 * <p>Path is {@code /api/_composite-unique-constraints} — the underscore
 * prefix keeps it out of the collection-name namespace served by
 * {@link DynamicCollectionRouter}.
 *
 * <ul>
 *   <li>{@code POST /} — body {@code {"collectionName":"availability","fieldNames":["title","provider","region"]}}
 *       creates the constraint and returns the resulting constraint name.</li>
 *   <li>{@code GET /?collection=availability} — lists all composite unique
 *       constraints currently defined on the collection's table.</li>
 *   <li>{@code DELETE /?collection=availability&fieldNames=title,provider,region}
 *       — drops the constraint that covers exactly those fields.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/_composite-unique-constraints")
public class CompositeUniqueConstraintController {

    private static final Logger logger = LoggerFactory.getLogger(CompositeUniqueConstraintController.class);

    private final CollectionRegistry registry;
    private final PhysicalTableStorageAdapter storage;

    public CompositeUniqueConstraintController(CollectionRegistry registry,
                                                PhysicalTableStorageAdapter storage) {
        this.registry = registry;
        this.storage = storage;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                       HttpServletRequest request) {
        setTenantContext(request);
        try {
            String collectionName = stringFromBody(body, "collectionName");
            List<String> fieldNames = stringListFromBody(body, "fieldNames");
            if (collectionName == null || collectionName.isBlank()) {
                return badRequest("'collectionName' is required");
            }
            if (fieldNames == null || fieldNames.size() < 2) {
                return badRequest("'fieldNames' must list at least 2 fields");
            }

            CollectionDefinition definition = registry.get(collectionName);
            if (definition == null) {
                return notFound("Collection not found: " + collectionName);
            }

            try {
                String constraintName = storage.addCompositeUniqueConstraint(definition, fieldNames);
                logger.info("Created composite unique constraint '{}' on '{}' covering {}",
                        constraintName, collectionName, fieldNames);
                return ResponseEntity.status(HttpStatus.CREATED).body(
                        envelope(definition, new CompositeUniqueConstraint(constraintName, fieldNames)));
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            }
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam("collection") String collectionName,
                                                     HttpServletRequest request) {
        setTenantContext(request);
        try {
            if (collectionName == null || collectionName.isBlank()) {
                return badRequest("'collection' query parameter is required");
            }
            CollectionDefinition definition = registry.get(collectionName);
            if (definition == null) {
                return notFound("Collection not found: " + collectionName);
            }

            List<CompositeUniqueConstraint> constraints = storage.listCompositeUniqueConstraints(definition);
            List<Map<String, Object>> items = new ArrayList<>();
            for (CompositeUniqueConstraint c : constraints) {
                items.add(toItem(definition, c));
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("data", items);
            return ResponseEntity.ok(resp);
        } finally {
            TenantContext.clear();
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> delete(@RequestParam("collection") String collectionName,
                                                       @RequestParam("fieldNames") String fieldNamesCsv,
                                                       HttpServletRequest request) {
        setTenantContext(request);
        try {
            if (collectionName == null || collectionName.isBlank()) {
                return badRequest("'collection' query parameter is required");
            }
            if (fieldNamesCsv == null || fieldNamesCsv.isBlank()) {
                return badRequest("'fieldNames' query parameter is required");
            }
            List<String> fieldNames = new ArrayList<>();
            for (String part : fieldNamesCsv.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) fieldNames.add(trimmed);
            }
            if (fieldNames.size() < 2) {
                return badRequest("'fieldNames' must list at least 2 fields");
            }

            CollectionDefinition definition = registry.get(collectionName);
            if (definition == null) {
                return notFound("Collection not found: " + collectionName);
            }

            boolean dropped;
            try {
                dropped = storage.dropCompositeUniqueConstraint(definition, fieldNames);
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            }
            if (!dropped) {
                return notFound("No composite unique constraint matches the requested field set");
            }
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }

    private static String stringFromBody(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListFromBody(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        if (!(v instanceof List<?> raw)) return null;
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o == null) continue;
            out.add(o.toString());
        }
        return out;
    }

    private static Map<String, Object> envelope(CollectionDefinition definition,
                                                 CompositeUniqueConstraint constraint) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("data", toItem(definition, constraint));
        return resp;
    }

    private static Map<String, Object> toItem(CollectionDefinition definition,
                                               CompositeUniqueConstraint constraint) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("collectionName", definition.name());
        item.put("constraintName", constraint.constraintName());
        item.put("fieldNames", constraint.fieldNames());
        return item;
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("errors", List.of(Map.of(
                "status", "400",
                "code", "BAD_REQUEST",
                "detail", message))));
    }

    private static ResponseEntity<Map<String, Object>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("errors", List.of(Map.of(
                "status", "404",
                "code", "NOT_FOUND",
                "detail", message))));
    }

    private static void setTenantContext(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        String tenantSlug = request.getHeader("X-Tenant-Slug");
        TenantContext.set(tenantId);
        TenantContext.setSlug(tenantSlug);
    }
}
