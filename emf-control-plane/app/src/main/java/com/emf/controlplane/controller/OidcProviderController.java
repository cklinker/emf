package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AddOidcProviderRequest;
import com.emf.controlplane.dto.OidcProviderDto;
import com.emf.controlplane.dto.UpdateOidcProviderRequest;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.service.OidcProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing OIDC provider configurations.
 * 
 * <p>Provides endpoints for listing, adding, updating, and deleting OIDC providers.
 * All endpoints require ADMIN role authorization.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>4.1: Return list of configured OIDC providers</li>
 *   <li>4.2: Add OIDC provider with valid configuration and return created provider</li>
 *   <li>4.4: Update OIDC provider and persist changes</li>
 *   <li>4.5: Delete OIDC provider by marking as inactive</li>
 * </ul>
 */
@RestController
@RequestMapping("/control/oidc/providers")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "OIDC Providers", description = "OIDC provider management APIs")
public class OidcProviderController {

    private static final Logger log = LoggerFactory.getLogger(OidcProviderController.class);

    private final OidcProviderService oidcProviderService;

    public OidcProviderController(OidcProviderService oidcProviderService) {
        this.oidcProviderService = oidcProviderService;
    }

    /**
     * Lists all active OIDC providers.
     * Requires ADMIN role authorization.
     * 
     * @return List of all active OIDC providers ordered by name
     * 
     * Validates: Requirement 4.1
     */
    @GetMapping
    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_CONNECTED_APPS')")
    @Operation(
            summary = "List OIDC providers",
            description = "Returns a list of all active OIDC providers. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved OIDC providers"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<OidcProviderDto>> listProviders() {
        log.debug("REST request to list all OIDC providers");
        
        List<OidcProvider> providers = oidcProviderService.listProviders();
        List<OidcProviderDto> dtos = providers.stream()
                .map(OidcProviderDto::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Adds a new OIDC provider.
     * Requires ADMIN role authorization.
     * 
     * @param request The provider creation request with name, issuer, and JWKS URI
     * @return The created OIDC provider with generated ID
     * 
     * Validates: Requirement 4.2
     */
    @PostMapping
    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_CONNECTED_APPS')")
    @Operation(
            summary = "Add OIDC provider",
            description = "Adds a new OIDC provider with the provided configuration. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "OIDC provider created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "409", description = "Conflict - provider with same name or issuer already exists")
    })
    public ResponseEntity<OidcProviderDto> addProvider(
            @Valid @RequestBody AddOidcProviderRequest request) {
        log.info("REST request to add OIDC provider: {}", request.getName());
        
        OidcProvider created = oidcProviderService.addProvider(request);
        OidcProviderDto dto = OidcProviderDto.fromEntity(created);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Updates an existing OIDC provider.
     * Only provided fields will be updated.
     * Requires ADMIN role authorization.
     * 
     * @param id The provider ID to update
     * @param request The update request with new values
     * @return The updated OIDC provider
     * 
     * Validates: Requirement 4.4
     */
    @PutMapping("/{id}")
    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_CONNECTED_APPS')")
    @Operation(
            summary = "Update OIDC provider",
            description = "Updates an existing OIDC provider. Only provided fields will be updated. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OIDC provider updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "OIDC provider not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - provider with same name or issuer already exists")
    })
    public ResponseEntity<OidcProviderDto> updateProvider(
            @Parameter(description = "OIDC provider ID", required = true)
            @PathVariable String id,
            @Valid @RequestBody UpdateOidcProviderRequest request) {
        log.info("REST request to update OIDC provider: {}", id);
        
        OidcProvider updated = oidcProviderService.updateProvider(id, request);
        OidcProviderDto dto = OidcProviderDto.fromEntity(updated);
        
        return ResponseEntity.ok(dto);
    }

    /**
     * Deletes an OIDC provider by marking it as inactive.
     * The provider is preserved in the database for audit purposes.
     * Requires ADMIN role authorization.
     * 
     * @param id The provider ID to delete
     * 
     * Validates: Requirement 4.5
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_CONNECTED_APPS')")
    @Operation(
            summary = "Delete OIDC provider",
            description = "Soft-deletes an OIDC provider by marking it as inactive. " +
                    "The provider is preserved in the database for audit purposes. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "OIDC provider deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "OIDC provider not found")
    })
    public ResponseEntity<Void> deleteProvider(
            @Parameter(description = "OIDC provider ID", required = true)
            @PathVariable String id) {
        log.info("REST request to delete OIDC provider: {}", id);
        
        oidcProviderService.deleteProvider(id);
        
        return ResponseEntity.noContent().build();
    }
}
