package com.emf.controlplane.controller;

import com.emf.controlplane.action.CollectionActionHandler;
import com.emf.controlplane.action.CollectionActionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for executing collection-scoped actions.
 *
 * <p>Provides generic endpoints for instance-level and collection-level actions
 * on system collections. Actions are dispatched to registered
 * {@link CollectionActionHandler} implementations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /control/{collection}/{id}/actions/{action} - Execute an instance action</li>
 *   <li>POST /control/{collection}/actions/{action} - Execute a collection action</li>
 * </ul>
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/control")
public class CollectionActionController {

    private static final Logger logger = LoggerFactory.getLogger(CollectionActionController.class);

    private final CollectionActionRegistry registry;

    public CollectionActionController(CollectionActionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Executes an instance-level action on a specific record.
     *
     * @param collection the collection name
     * @param id the record ID
     * @param action the action name
     * @param body the optional request body
     * @param request the HTTP request
     * @return the action result, or 404 if no handler is found
     */
    @PostMapping("/{collection}/{id}/actions/{action}")
    public ResponseEntity<?> executeInstanceAction(
            @PathVariable String collection,
            @PathVariable String id,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        logger.debug("Instance action request: collection='{}', id='{}', action='{}'",
                collection, id, action);

        return registry.find(collection, action)
                .map(handler -> {
                    if (!handler.isInstanceAction()) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "error", "Action '" + action + "' is a collection-level action, not an instance action"));
                    }
                    String tenantId = request.getHeader("X-Tenant-ID");
                    String userId = request.getHeader("X-User-Id");
                    Object result = handler.execute(id, body, tenantId, userId);
                    return result != null
                            ? ResponseEntity.ok(result)
                            : ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Executes a collection-level action.
     *
     * @param collection the collection name
     * @param action the action name
     * @param body the optional request body
     * @param request the HTTP request
     * @return the action result, or 404 if no handler is found
     */
    @PostMapping("/{collection}/actions/{action}")
    public ResponseEntity<?> executeCollectionAction(
            @PathVariable String collection,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        logger.debug("Collection action request: collection='{}', action='{}'",
                collection, action);

        return registry.find(collection, action)
                .map(handler -> {
                    if (handler.isInstanceAction()) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "error", "Action '" + action + "' is an instance-level action, requires a record ID"));
                    }
                    String tenantId = request.getHeader("X-Tenant-ID");
                    String userId = request.getHeader("X-User-Id");
                    Object result = handler.execute(null, body, tenantId, userId);
                    return result != null
                            ? ResponseEntity.ok(result)
                            : ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
