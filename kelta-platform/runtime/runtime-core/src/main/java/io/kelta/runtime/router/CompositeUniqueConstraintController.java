package io.kelta.runtime.router;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.CompositeUniqueConstraint;
import io.kelta.runtime.storage.PhysicalTableStorageAdapter;
import io.kelta.runtime.storage.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST endpoints for composite (multi-column) unique constraints on a
 * collection's physical table. Single-column uniqueness is handled by the
 * per-field {@code unique} flag on {@code /api/fields}; this controller
 * covers the cases where the constraint spans two or more columns, e.g.
 * {@code (title, provider, region)} on Availability or
 * {@code (season, episodeNumber)} on Episode.
 *
 * <p>Constraints are stored as {@code ALTER TABLE ... ADD CONSTRAINT
 * <name> UNIQUE (cols...)} on the collection's physical table. They are
 * enforced by PostgreSQL itself — INSERT/UPDATE attempts that would
 * duplicate the constrained tuple raise {@link
 * io.kelta.runtime.storage.UniqueConstraintViolationException}, which the
 * {@link GlobalExceptionHandler} maps to HTTP 409 Conflict.
 *
 * <p>The endpoint path is prefixed with an underscore (a leading character
 * that cannot appear in user-defined collection names) so requests do not
 * collide with {@link DynamicCollectionRouter}'s
 * {@code /{collectionName}} catch-all.
 */
@RestController
@RequestMapping("/api/_composite-unique-constraints")
public class CompositeUniqueConstraintController {

    private static final Logger logger = LoggerFactory.getLogger(
        CompositeUniqueConstraintController.class);

    static final String RESOURCE_TYPE = "compositeUniqueConstraints";

    private final CollectionRegistry registry;
    private final PhysicalTableStorageAdapter storageAdapter;

    public CompositeUniqueConstraintController(
            CollectionRegistry registry,
            PhysicalTableStorageAdapter storageAdapter) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.storageAdapter = Objects.requireNonNull(storageAdapter, "storageAdapter");
    }

    /**
     * Creates a composite unique constraint on the named collection.
     *
     * <p>Request body (JSON:API):
     * <pre>{@code
     * {
     *   "data": {
     *     "type": "compositeUniqueConstraints",
     *     "attributes": {
     *       "collectionName": "availability",
     *       "fieldNames": ["title", "provider", "region"]
     *     }
     *   }
     * }
     * }</pre>
     *
     * <p>Returns 201 with the resource document, or 404 if the collection
     * does not exist, or 400 if the field list is invalid.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        setTenantContext(request);
        try {
            Map<String, Object> attrs = extractAttributes(body);
            String collectionName = stringAttr(attrs, "collectionName");
            List<String> fieldNames = listAttr(attrs, "fieldNames");
            if (collectionName == null || collectionName.isBlank()) {
                return badRequest("attribute 'collectionName' is required");
            }
            if (fieldNames == null || fieldNames.size() < 2) {
                return badRequest(
                    "attribute 'fieldNames' must contain at least 2 field names");
            }

            CollectionDefinition definition = registry.get(collectionName);
            if (definition == null) {
                return ResponseEntity.notFound().build();
            }

            try {
                String name = storageAdapter.createCompositeUniqueConstraint(
                    definition, fieldNames);
                Map<String, Object> response = toResourceDocument(
                    new CompositeUniqueConstraint(name, fieldNames), collectionName);
                logger.info("Created composite unique constraint '{}' on '{}' over {}",
                    name, collectionName, fieldNames);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            } catch (StorageException e) {
                logger.error("Failed to create composite unique constraint on '{}': {}",
                    collectionName, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("500", "storageError", e.getMessage()));
            }
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Lists composite unique constraints on the named collection. Returns
     * an empty list if the collection has none.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam("collectionName") String collectionName,
            HttpServletRequest request) {
        setTenantContext(request);
        try {
            if (collectionName == null || collectionName.isBlank()) {
                return badRequest("query parameter 'collectionName' is required");
            }
            CollectionDefinition definition = registry.get(collectionName);
            if (definition == null) {
                return ResponseEntity.notFound().build();
            }
            List<CompositeUniqueConstraint> constraints =
                storageAdapter.listCompositeUniqueConstraints(definition);
            List<Map<String, Object>> data = new ArrayList<>(constraints.size());
            for (CompositeUniqueConstraint c : constraints) {
                data.add(toResource(c, collectionName));
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Drops a composite unique constraint by name. Idempotent: returns
     * 204 whether or not the constraint existed.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable("name") String name,
            @RequestParam("collectionName") String collectionName,
            HttpServletRequest request) {
        setTenantContext(request);
        try {
            if (collectionName == null || collectionName.isBlank()) {
                return badRequest("query parameter 'collectionName' is required");
            }
            CollectionDefinition definition = registry.get(collectionName);
            if (definition == null) {
                return ResponseEntity.notFound().build();
            }
            try {
                storageAdapter.dropCompositeUniqueConstraint(definition, name);
                return ResponseEntity.noContent().build();
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            }
        } finally {
            TenantContext.clear();
        }
    }

    private Map<String, Object> toResourceDocument(
            CompositeUniqueConstraint constraint, String collectionName) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("data", toResource(constraint, collectionName));
        return document;
    }

    private Map<String, Object> toResource(
            CompositeUniqueConstraint constraint, String collectionName) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", RESOURCE_TYPE);
        resource.put("id", constraint.name());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("collectionName", collectionName);
        attributes.put("name", constraint.name());
        attributes.put("fieldNames", constraint.fieldNames());
        resource.put("attributes", attributes);
        return resource;
    }

    private void setTenantContext(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        String tenantSlug = request.getHeader("X-Tenant-Slug");
        TenantContext.set(tenantId);
        TenantContext.setSlug(tenantSlug);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAttributes(Map<String, Object> body) {
        if (body == null) return Map.of();
        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object attrObj = ((Map<String, Object>) data).get("attributes");
            if (attrObj instanceof Map<?, ?> attrs) {
                return (Map<String, Object>) attrs;
            }
        }
        return Map.of();
    }

    private static String stringAttr(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> listAttr(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        if (!(v instanceof List<?> raw)) return null;
        List<String> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item == null) return null;
            out.add(item.toString());
        }
        return out;
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(error("400", "invalidRequest", message));
    }

    private static Map<String, Object> error(String status, String code, String detail) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", status);
        err.put("code", code);
        err.put("detail", detail);
        return Map.of("errors", List.of(err));
    }
}
