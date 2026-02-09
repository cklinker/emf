package com.emf.controlplane.controller;

import com.emf.controlplane.dto.ResourceDiscoveryDto;
import com.emf.controlplane.service.DiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for resource discovery.
 * Provides an endpoint for discovering all active collections with their schemas,
 * available operations, and authorization hints.
 * 
 * <p>This endpoint is useful for domain services and API consumers that need to
 * dynamically discover available resources and their capabilities.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.1: Return all active collections with their schemas</li>
 * </ul>
 */
@RestController
@RequestMapping("/control/_meta")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Discovery", description = "Resource discovery APIs")
public class DiscoveryController {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryController.class);

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * Discovers all active resources (collections) with their complete metadata.
     * Returns information about schemas, available operations, and authorization hints.
     * 
     * <p>This endpoint is designed for:
     * <ul>
     *   <li>Domain services that need to understand available collections</li>
     *   <li>API consumers that want to dynamically interact with the platform</li>
     *   <li>UI components that need to render forms based on collection schemas</li>
     * </ul>
     * 
     * @return ResourceDiscoveryDto containing metadata for all active collections
     * 
     * Validates: Requirement 8.1
     */
    @GetMapping("/resources")
    @Operation(
            summary = "Discover resources",
            description = "Returns metadata about all active collections including their schemas, " +
                    "available operations, and authorization hints. This endpoint is useful for " +
                    "domain services and API consumers that need to dynamically discover available " +
                    "resources and their capabilities."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved resource metadata",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResourceDiscoveryDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - invalid or missing JWT token"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    public ResponseEntity<ResourceDiscoveryDto> discoverResources() {
        log.debug("REST request to discover resources");
        
        ResourceDiscoveryDto discovery = discoveryService.discoverResources();
        
        log.info("Discovered {} resources", discovery.getResources().size());
        return ResponseEntity.ok(discovery);
    }
}
