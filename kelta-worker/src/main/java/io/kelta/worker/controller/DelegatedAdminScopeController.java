package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.SecurityAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD for delegated-admin scopes ({@code delegated-admin-scopes} system collection). All
 * endpoints require {@code MANAGE_DELEGATED_ADMINS} — enforced in-controller because
 * {@code /api/admin/**} is a static gateway route with only the blanket API_ACCESS check.
 *
 * <p>Writes go through {@link QueryEngine} so the scope-definition gate
 * ({@code DelegatedAdminScopeValidationHook}: privileged-profile rejection, id existence,
 * shape/caps) and the identity-collection guard fire on every mutation.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/delegated-admin-scopes")
public class DelegatedAdminScopeController {

    private static final Logger log = LoggerFactory.getLogger(DelegatedAdminScopeController.class);
    private static final String PERMISSION = "MANAGE_DELEGATED_ADMINS";
    private static final String COLLECTION = "delegated-admin-scopes";
    private static final int MAX_PAGE = 200;

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;

    public DelegatedAdminScopeController(QueryEngine queryEngine,
                                         CollectionRegistry collectionRegistry,
                                         CerbosPermissionResolver permissionResolver,
                                         BootstrapRepository bootstrapRepository) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request,
                                                    @RequestParam(defaultValue = "50") int limit,
                                                    @RequestParam(defaultValue = "1") int page) {
        requirePermission(request);
        requireTenant();
        QueryResult result = queryEngine.executeQuery(definition(), new QueryRequest(
                new Pagination(Math.max(1, page), Math.min(Math.max(1, limit), MAX_PAGE)),
                List.of(), List.of(), List.<FilterCondition>of()));
        List<Map<String, Object>> records = result.data().stream().map(this::project).toList();
        return ResponseEntity.ok(JsonApiResponseBuilder.collection(COLLECTION, records));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(HttpServletRequest request, @PathVariable String id) {
        requirePermission(request);
        requireTenant();
        Map<String, Object> row = load(id);
        return ResponseEntity.ok(JsonApiResponseBuilder.single(COLLECTION, id, project(row)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request,
                                                      @RequestBody Map<String, Object> body) {
        requirePermission(request);
        String tenantId = requireTenant();
        Map<String, Object> data = new LinkedHashMap<>(attributes(body));
        if (str(data.get("name")) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        // Direct queryEngine.create must set tenantId itself (the JSON:API layer injects it on
        // the HTTP path; without it the NOT NULL tenant_id is violated).
        data.put("tenantId", tenantId);
        Map<String, Object> created = queryEngine.create(definition(), data);
        String id = String.valueOf(created.get("id"));
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.DELEGATED_SCOPE_CHANGED,
                actor(request), id, tenantId, "success", "created");
        log.info("Delegated-admin scope {} created in tenant {}", id, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JsonApiResponseBuilder.single(COLLECTION, id, project(created)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(HttpServletRequest request, @PathVariable String id,
                                                      @RequestBody Map<String, Object> body) {
        requirePermission(request);
        String tenantId = requireTenant();
        load(id);
        Map<String, Object> data = new LinkedHashMap<>(attributes(body));
        data.remove("tenantId");
        Map<String, Object> updated = queryEngine.update(definition(), id, data)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scope not found"));
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.DELEGATED_SCOPE_CHANGED,
                actor(request), id, tenantId, "success", "updated");
        return ResponseEntity.ok(JsonApiResponseBuilder.single(COLLECTION, id, project(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String id) {
        requirePermission(request);
        String tenantId = requireTenant();
        load(id);
        boolean deleted = queryEngine.delete(definition(), id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scope not found");
        }
        SecurityAuditLogger.log(SecurityAuditLogger.EventType.DELEGATED_SCOPE_CHANGED,
                actor(request), id, tenantId, "success", "deleted");
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------- Helpers

    private CollectionDefinition definition() {
        CollectionDefinition definition = collectionRegistry.get(COLLECTION);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "delegated-admin-scopes collection not registered");
        }
        return definition;
    }

    private Map<String, Object> load(String id) {
        return queryEngine.getById(definition(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scope not found"));
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> attrs = new LinkedHashMap<>(row);
        attrs.remove("id");
        attrs.remove("tenantId");
        return attrs;
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

    private String requireTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tenant context");
        }
        return tenantId;
    }

    private String actor(HttpServletRequest request) {
        String email = permissionResolver.getEmail(request);
        return email != null ? email : "unknown";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attributes(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }
        Object data = body.get("data");
        if (data instanceof Map<?, ?> dataMap && dataMap.get("attributes") instanceof Map<?, ?> attrs) {
            return (Map<String, Object>) attrs;
        }
        return body;
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
