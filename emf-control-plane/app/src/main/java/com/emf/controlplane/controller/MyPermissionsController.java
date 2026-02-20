package com.emf.controlplane.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for fetching the current user's effective permissions.
 *
 * <p>Returns object-level CRUD permissions and field-level visibility for
 * a given collection. Currently returns permissive defaults (all permissions
 * granted, all fields visible) until the full permission engine is implemented.
 *
 * <p>These endpoints are consumed by the app UI hooks:
 * <ul>
 *   <li>{@code useObjectPermissions} — GET /control/my-permissions/objects/{collectionName}</li>
 *   <li>{@code useFieldPermissions} — GET /control/my-permissions/fields/{collectionName}</li>
 * </ul>
 */
@RestController
@RequestMapping("/control/my-permissions")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "My Permissions", description = "Current user effective permissions")
public class MyPermissionsController {

    private static final Logger log = LoggerFactory.getLogger(MyPermissionsController.class);

    /**
     * Get the current user's effective object-level permissions for a collection.
     *
     * <p>Returns CRUD permission flags. Currently returns all-permissive defaults
     * until the permission engine is fully implemented.
     *
     * @param collectionName the collection API name
     * @return object permission flags
     */
    @GetMapping("/objects/{collectionName}")
    @Operation(
            summary = "Get object permissions",
            description = "Returns effective object-level CRUD permissions for the current user on a collection"
    )
    @ApiResponse(responseCode = "200", description = "Permission flags returned")
    public ResponseEntity<Map<String, Boolean>> getObjectPermissions(
            @Parameter(description = "Collection API name") @PathVariable String collectionName) {
        log.debug("Fetching object permissions for collection: {}", collectionName);

        // TODO: Implement real permission resolution from profiles/permission sets
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        permissions.put("canCreate", true);
        permissions.put("canRead", true);
        permissions.put("canEdit", true);
        permissions.put("canDelete", true);
        permissions.put("canViewAll", true);
        permissions.put("canModifyAll", true);

        return ResponseEntity.ok(permissions);
    }

    /**
     * Get the current user's effective field-level permissions for a collection.
     *
     * <p>Returns a list of field visibility overrides. Fields not in the response
     * default to VISIBLE. Currently returns an empty list (all fields visible)
     * until the permission engine is fully implemented.
     *
     * @param collectionName the collection API name
     * @return list of field permission overrides (empty = all visible)
     */
    @GetMapping("/fields/{collectionName}")
    @Operation(
            summary = "Get field permissions",
            description = "Returns effective field-level visibility for the current user on a collection"
    )
    @ApiResponse(responseCode = "200", description = "Field permissions returned")
    public ResponseEntity<List<Object>> getFieldPermissions(
            @Parameter(description = "Collection API name") @PathVariable String collectionName) {
        log.debug("Fetching field permissions for collection: {}", collectionName);

        // TODO: Implement real field-level permission resolution
        // Empty list means all fields default to VISIBLE
        return ResponseEntity.ok(Collections.emptyList());
    }
}
