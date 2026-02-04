package com.emf.controlplane.controller;

import com.emf.controlplane.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for admin dashboard and monitoring endpoints.
 * 
 * <p>Provides dashboard data including system health, metrics, and recent errors.
 */
@RestController
@RequestMapping("/api/_admin")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Admin", description = "Admin dashboard and monitoring APIs")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    
    private final DashboardService dashboardService;

    public AdminController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Returns dashboard data including health status, metrics, and recent errors.
     * 
     * @param timeRange Time range for metrics (5m, 15m, 1h, 6h, 24h)
     * @return Dashboard data
     */
    @GetMapping("/dashboard")
    @Operation(
            summary = "Get dashboard data",
            description = "Returns system health status, metrics, and recent errors for the dashboard"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getDashboard(
            @Parameter(description = "Time range for metrics (5m, 15m, 1h, 6h, 24h)")
            @RequestParam(required = false, defaultValue = "15m") String timeRange) {
        
        log.debug("REST request to get dashboard data - timeRange: {}", timeRange);
        
        Map<String, Object> dashboard = dashboardService.getDashboardData(timeRange);
        return ResponseEntity.ok(dashboard);
    }
}
