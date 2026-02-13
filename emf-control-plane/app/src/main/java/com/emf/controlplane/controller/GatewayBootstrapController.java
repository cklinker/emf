package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiPage;
import com.emf.controlplane.repository.OidcProviderRepository;
import com.emf.controlplane.service.GatewayBootstrapService;
import com.emf.controlplane.service.UiConfigService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for gateway bootstrap configuration.
 * 
 * Provides the /control/bootstrap endpoint that returns services, collections,
 * and authorization policies needed by the API Gateway for routing.
 * 
 * This endpoint is public (no authentication required) to allow the gateway
 * to fetch configuration during startup.
 */
@RestController
@RequestMapping("/control")
@Tag(name = "Gateway Bootstrap", description = "Gateway bootstrap configuration APIs")
public class GatewayBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(GatewayBootstrapController.class);

    private final GatewayBootstrapService gatewayBootstrapService;
    private final UiConfigService uiConfigService;
    private final OidcProviderRepository oidcProviderRepository;

    public GatewayBootstrapController(GatewayBootstrapService gatewayBootstrapService,
                                      UiConfigService uiConfigService,
                                      OidcProviderRepository oidcProviderRepository) {
        this.gatewayBootstrapService = gatewayBootstrapService;
        this.uiConfigService = uiConfigService;
        this.oidcProviderRepository = oidcProviderRepository;
    }

    /**
     * Gets the bootstrap configuration for the API Gateway.
     * Returns all active services, collections, and authorization policies.
     * This endpoint is accessible without authentication.
     *
     * @return Bootstrap configuration containing services, collections, and authorization
     */
    @GetMapping("/bootstrap")
    @Operation(
            summary = "Get gateway bootstrap configuration",
            description = "Returns the configuration needed by the API Gateway on startup, " +
                    "including services, collections, and authorization policies. " +
                    "This endpoint is public (no authentication required)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved bootstrap configuration")
    })
    public ResponseEntity<GatewayBootstrapConfigDto> getGatewayBootstrapConfig() {
        log.debug("REST request to get gateway bootstrap configuration");
        
        GatewayBootstrapConfigDto dto = gatewayBootstrapService.getBootstrapConfig();
        
        return ResponseEntity.ok(dto);
    }

    /**
     * Gets the bootstrap configuration for the Admin UI.
     * Returns all active pages, menus, theme settings, branding, features, and OIDC providers.
     * This endpoint is accessible without authentication.
     *
     * @return Bootstrap configuration for the UI
     */
    @GetMapping("/ui-bootstrap")
    @Operation(
            summary = "Get UI bootstrap configuration",
            description = "Returns the initial configuration needed by the Admin UI on startup, " +
                    "including pages, menus, theme, branding, features, and OIDC providers. " +
                    "This endpoint is public (no authentication required)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved UI bootstrap configuration")
    })
    public ResponseEntity<BootstrapConfigDto> getUiBootstrapConfig() {
        log.debug("REST request to get UI bootstrap configuration");

        UiConfigService.BootstrapConfig config = uiConfigService.getBootstrapConfig();

        List<UiPageDto> pageDtos = config.getPages().stream()
                .map(UiPageDto::fromEntity)
                .collect(Collectors.toList());

        List<UiMenuDto> menuDtos = config.getMenus().stream()
                .map(UiMenuDto::fromEntity)
                .collect(Collectors.toList());

        BootstrapConfigDto.ThemeConfig theme = new BootstrapConfigDto.ThemeConfig(
                "#1976d2", "#dc004e", "Inter, system-ui, sans-serif", "8px"
        );

        BootstrapConfigDto.BrandingConfig branding = new BootstrapConfigDto.BrandingConfig(
                "/logo.svg", "EMF Control Plane", "/favicon.ico"
        );

        BootstrapConfigDto.FeatureFlags features = new BootstrapConfigDto.FeatureFlags(
                true, true, true, true, true
        );

        // Return tenant-scoped OIDC providers when tenant context is set (slug-based request),
        // otherwise return all active providers (gateway internal bootstrap)
        String tenantId = TenantContextHolder.getTenantId();
        List<BootstrapConfigDto.OidcProviderSummary> oidcProviders;
        if (tenantId != null) {
            oidcProviders = oidcProviderRepository.findByTenantIdAndActiveTrue(tenantId)
                    .stream()
                    .map(p -> new BootstrapConfigDto.OidcProviderSummary(
                            p.getId(), p.getName(), p.getIssuer(), p.getClientId(),
                            p.getRolesClaim(), p.getRolesMapping()))
                    .collect(Collectors.toList());
        } else {
            oidcProviders = oidcProviderRepository.findByActiveTrue()
                    .stream()
                    .map(p -> new BootstrapConfigDto.OidcProviderSummary(
                            p.getId(), p.getName(), p.getIssuer(), p.getClientId(),
                            p.getRolesClaim(), p.getRolesMapping()))
                    .collect(Collectors.toList());
        }

        String tenantSlug = TenantContextHolder.getTenantSlug();
        BootstrapConfigDto bootstrapDto = new BootstrapConfigDto(
                pageDtos, menuDtos, theme, branding, features, oidcProviders, tenantId, tenantSlug
        );

        return ResponseEntity.ok(bootstrapDto);
    }
}
