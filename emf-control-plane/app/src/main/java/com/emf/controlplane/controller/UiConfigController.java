package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiPage;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.emf.controlplane.service.UiConfigService;
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
 * REST controller for managing UI configuration.
 *
 * <p>Provides endpoints for bootstrap configuration, pages, and menus.
 * Most endpoints require ADMIN role authorization, except for bootstrap config.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>5.1: Return bootstrap configuration including pages and menus</li>
 *   <li>5.2: Return list of UI pages</li>
 *   <li>5.3: Create UI page with valid data and return created page</li>
 *   <li>5.4: Update UI page and persist changes</li>
 *   <li>5.5: Return list of UI menus</li>
 *   <li>5.6: Update UI menu and persist changes</li>
 * </ul>
 */
@RestController
@RequestMapping("/ui")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "UI Configuration", description = "UI configuration management APIs")
public class UiConfigController {

    private static final Logger log = LoggerFactory.getLogger(UiConfigController.class);

    private final UiConfigService uiConfigService;
    private final OidcProviderRepository oidcProviderRepository;

    public UiConfigController(UiConfigService uiConfigService, OidcProviderRepository oidcProviderRepository) {
        this.uiConfigService = uiConfigService;
        this.oidcProviderRepository = oidcProviderRepository;
    }

    /**
     * Gets the bootstrap configuration for the UI.
     * Returns all active pages, menus, theme settings, branding, features, and OIDC providers.
     * This endpoint is accessible without authentication.
     *
     * @return Bootstrap configuration containing pages, menus, theme, branding, features, and oidcProviders
     *
     * Validates: Requirement 5.1
     */
    @GetMapping("/config/bootstrap")
    @Operation(
            summary = "Get bootstrap configuration",
            description = "Returns the initial configuration needed by the UI on startup, " +
                    "including pages, menus, theme, branding, features, and OIDC providers."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved bootstrap configuration")
    })
    public ResponseEntity<BootstrapConfigDto> getBootstrapConfig() {
        log.debug("REST request to get bootstrap configuration");

        UiConfigService.BootstrapConfig config = uiConfigService.getBootstrapConfig();

        List<UiPageDto> pageDtos = config.getPages().stream()
                .map(UiPageDto::fromEntity)
                .collect(Collectors.toList());

        List<UiMenuDto> menuDtos = config.getMenus().stream()
                .map(UiMenuDto::fromEntity)
                .collect(Collectors.toList());

        // Default theme - can be extended to load from database/config
        BootstrapConfigDto.ThemeConfig theme = new BootstrapConfigDto.ThemeConfig(
                "#1976d2",  // primaryColor
                "#dc004e",  // secondaryColor
                "Inter, system-ui, sans-serif",  // fontFamily
                "8px"  // borderRadius
        );

        // Default branding - can be extended to load from database/config
        BootstrapConfigDto.BrandingConfig branding = new BootstrapConfigDto.BrandingConfig(
                "/logo.svg",  // logoUrl
                "EMF Control Plane",  // applicationName
                "/favicon.ico"  // faviconUrl
        );

        // Default features - can be extended to load from database/config
        BootstrapConfigDto.FeatureFlags features = new BootstrapConfigDto.FeatureFlags(
                true,   // enableBuilder
                true,   // enableResourceBrowser
                true,   // enablePackages
                true,   // enableMigrations
                true    // enableDashboard
        );

        // Get active OIDC providers for login
        List<BootstrapConfigDto.OidcProviderSummary> oidcProviders = oidcProviderRepository.findByActiveTrue()
                .stream()
                .map(p -> new BootstrapConfigDto.OidcProviderSummary(p.getId(), p.getName(), p.getIssuer(), p.getClientId()))
                .collect(Collectors.toList());

        BootstrapConfigDto dto = new BootstrapConfigDto(
                pageDtos,
                menuDtos,
                theme,
                branding,
                features,
                oidcProviders
        );

        return ResponseEntity.ok(dto);
    }

    /**
     * Lists all active UI pages.
     * Requires ADMIN role authorization.
     *
     * @return List of all active UI pages ordered by name
     *
     * Validates: Requirement 5.2
     */
    @GetMapping("/pages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List UI pages",
            description = "Returns a list of all active UI pages. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved UI pages"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<UiPageDto>> listPages() {
        log.debug("REST request to list all UI pages");

        List<UiPage> pages = uiConfigService.listPages();
        List<UiPageDto> dtos = pages.stream()
                .map(UiPageDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Creates a new UI page.
     * Requires ADMIN role authorization.
     *
     * @param request The page creation request with name, path, title, and config
     * @return The created UI page with generated ID
     *
     * Validates: Requirement 5.3
     */
    @PostMapping("/pages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create UI page",
            description = "Creates a new UI page with the provided configuration. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "UI page created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "409", description = "Conflict - page with same path already exists")
    })
    public ResponseEntity<UiPageDto> createPage(
            @Valid @RequestBody CreateUiPageRequest request) {
        log.info("REST request to create UI page: {}", request.getName());

        UiPage created = uiConfigService.createPage(request);
        UiPageDto dto = UiPageDto.fromEntity(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Updates an existing UI page.
     * Only provided fields will be updated.
     * Requires ADMIN role authorization.
     *
     * @param id The page ID to update
     * @param request The update request with new values
     * @return The updated UI page
     *
     * Validates: Requirement 5.4
     */
    @PutMapping("/pages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update UI page",
            description = "Updates an existing UI page. Only provided fields will be updated. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "UI page updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "UI page not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - page with same path already exists")
    })
    public ResponseEntity<UiPageDto> updatePage(
            @Parameter(description = "UI page ID", required = true)
            @PathVariable String id,
            @Valid @RequestBody UpdateUiPageRequest request) {
        log.info("REST request to update UI page: {}", id);

        UiPage updated = uiConfigService.updatePage(id, request);
        UiPageDto dto = UiPageDto.fromEntity(updated);

        return ResponseEntity.ok(dto);
    }

    /**
     * Lists all UI menus.
     * Requires ADMIN role authorization.
     *
     * @return List of all UI menus ordered by name
     *
     * Validates: Requirement 5.5
     */
    @GetMapping("/menus")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List UI menus",
            description = "Returns a list of all UI menus. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved UI menus"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<UiMenuDto>> listMenus() {
        log.debug("REST request to list all UI menus");

        List<UiMenu> menus = uiConfigService.listMenus();
        List<UiMenuDto> dtos = menus.stream()
                .map(UiMenuDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Updates an existing UI menu.
     * Only provided fields will be updated.
     * If items are provided, they will replace the existing items.
     * Requires ADMIN role authorization.
     *
     * @param id The menu ID to update
     * @param request The update request with new values
     * @return The updated UI menu
     *
     * Validates: Requirement 5.6
     */
    @PutMapping("/menus/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update UI menu",
            description = "Updates an existing UI menu. Only provided fields will be updated. " +
                    "If items are provided, they will replace the existing items. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "UI menu updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "UI menu not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - menu with same name already exists")
    })
    public ResponseEntity<UiMenuDto> updateMenu(
            @Parameter(description = "UI menu ID", required = true)
            @PathVariable String id,
            @Valid @RequestBody UpdateUiMenuRequest request) {
        log.info("REST request to update UI menu: {}", id);

        UiMenu updated = uiConfigService.updateMenu(id, request);
        UiMenuDto dto = UiMenuDto.fromEntity(updated);

        return ResponseEntity.ok(dto);
    }
}
