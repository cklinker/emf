package com.emf.controlplane.controller;

import com.emf.controlplane.dto.ExportPackageRequest;
import com.emf.controlplane.dto.ImportPackageRequest;
import com.emf.controlplane.dto.ImportResultDto;
import com.emf.controlplane.dto.PackageDto;
import com.emf.controlplane.service.PackageService;
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

/**
 * REST controller for managing configuration packages.
 * 
 * <p>Provides endpoints for exporting and importing configuration packages
 * for environment promotion. All endpoints require ADMIN role authorization.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>6.1: Export selected configuration items to a package</li>
 *   <li>6.2: Import package with dry-run mode to preview changes</li>
 *   <li>6.3: Apply package import and persist changes</li>
 * </ul>
 */
@RestController
@RequestMapping("/control/packages")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Packages", description = "Configuration package management APIs")
public class PackageController {

    private static final Logger log = LoggerFactory.getLogger(PackageController.class);

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    /**
     * Retrieves the history of package operations (exports and imports).
     * Returns a list of all packages that have been exported or imported.
     * Requires ADMIN role authorization.
     * 
     * @return List of package history records
     */
    @GetMapping("/history")
    @PreAuthorize("@securityService.hasPermission(#root, 'CUSTOMIZE_APPLICATION')")
    @Operation(
            summary = "Get package history",
            description = "Retrieves the history of all package export and import operations. " +
                    "Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Package history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<java.util.List<PackageDto>> getPackageHistory() {
        log.info("REST request to get package history");
        
        java.util.List<PackageDto> history = packageService.getPackageHistory();
        
        return ResponseEntity.ok(history);
    }

    /**
     * Exports selected configuration items to a package.
     * The package can be used to promote configuration between environments.
     * Requires ADMIN role authorization.
     * 
     * @param request The export request with item selection
     * @return The exported package with all selected items
     * 
     * Validates: Requirement 6.1
     */
    @PostMapping("/export")
    @PreAuthorize("@securityService.hasPermission(#root, 'CUSTOMIZE_APPLICATION')")
    @Operation(
            summary = "Export configuration package",
            description = "Exports selected configuration items (collections, roles, policies, " +
                    "OIDC providers, UI pages, menus) to a portable package. " +
                    "The package can be imported into another environment. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Package exported successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "One or more selected items not found")
    })
    public ResponseEntity<PackageDto> exportPackage(
            @Valid @RequestBody ExportPackageRequest request) {
        log.info("REST request to export package: {} v{}", request.getName(), request.getVersion());
        
        PackageDto exported = packageService.exportPackage(request);
        
        return ResponseEntity.ok(exported);
    }

    /**
     * Imports a configuration package with optional dry-run mode.
     * In dry-run mode, validates and previews changes without applying them.
     * Requires ADMIN role authorization.
     * 
     * @param request The import request with package data
     * @param dryRun If true, only preview changes without applying (default: false)
     * @return The import result with details of changes made or to be made
     * 
     * Validates: Requirements 6.2, 6.3
     */
    @PostMapping("/import")
    @PreAuthorize("@securityService.hasPermission(#root, 'CUSTOMIZE_APPLICATION')")
    @Operation(
            summary = "Import configuration package",
            description = "Imports a configuration package into the current environment. " +
                    "Use dryRun=true to preview changes without applying them. " +
                    "The conflictStrategy in the request determines how to handle existing items: " +
                    "SKIP (default), OVERWRITE, or FAIL. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Package imported successfully (or dry-run preview)"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors or package validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "409", description = "Conflict - items already exist and conflict strategy is FAIL")
    })
    public ResponseEntity<ImportResultDto> importPackage(
            @Valid @RequestBody ImportPackageRequest request,
            @Parameter(description = "If true, preview changes without applying them")
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("REST request to import package: {} (dryRun={})", 
                request.getPackageData() != null ? request.getPackageData().getName() : "null", dryRun);
        
        ImportResultDto result = packageService.importPackage(request, dryRun);
        
        if (!result.isSuccess()) {
            // Return 400 for validation failures, but still include the result details
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        
        return ResponseEntity.ok(result);
    }
}
