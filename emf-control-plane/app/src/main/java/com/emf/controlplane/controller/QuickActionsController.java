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
import java.util.List;

/**
 * REST controller for quick action definitions.
 *
 * <p>Returns available quick actions for a collection. Currently returns
 * an empty list until the quick actions feature is implemented.
 *
 * <p>Consumed by the app UI hook:
 * <ul>
 *   <li>{@code useQuickActions} — GET /control/quick-actions/{collectionName}</li>
 * </ul>
 */
@RestController
@RequestMapping("/control/quick-actions")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Quick Actions", description = "Collection quick action definitions")
public class QuickActionsController {

    private static final Logger log = LoggerFactory.getLogger(QuickActionsController.class);

    /**
     * Get quick actions available for a collection.
     *
     * <p>Returns quick action definitions filtered by the current user's permissions.
     * Currently returns an empty list until quick actions are implemented.
     *
     * @param collectionName the collection API name
     * @return list of quick action definitions
     */
    @GetMapping("/{collectionName}")
    @Operation(
            summary = "Get quick actions",
            description = "Returns available quick actions for a collection"
    )
    @ApiResponse(responseCode = "200", description = "Quick actions returned")
    public ResponseEntity<List<Object>> getQuickActions(
            @Parameter(description = "Collection API name") @PathVariable String collectionName) {
        log.debug("Fetching quick actions for collection: {}", collectionName);

        // TODO: Implement quick actions feature
        return ResponseEntity.ok(Collections.emptyList());
    }
}
