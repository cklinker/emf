package com.emf.controlplane.controller;

import com.emf.controlplane.dto.GatewayBootstrapConfigDto;
import com.emf.controlplane.service.GatewayBootstrapService;
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

    public GatewayBootstrapController(GatewayBootstrapService gatewayBootstrapService) {
        this.gatewayBootstrapService = gatewayBootstrapService;
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
}
