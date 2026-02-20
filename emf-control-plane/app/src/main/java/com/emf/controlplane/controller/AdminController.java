package com.emf.controlplane.controller;

import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.service.DashboardService;
import com.emf.controlplane.service.GovernorLimitsService;
import com.emf.controlplane.service.GovernorLimitsService.GovernorLimitsStatus;
import com.emf.controlplane.tenant.TenantContextHolder;
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
 * <p>Provides dashboard data including tenant usage, governor limits, and system health.
 */
@RestController
@RequestMapping("/control/_admin")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Admin", description = "Admin dashboard and monitoring APIs")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final DashboardService dashboardService;
    private final GovernorLimitsService governorLimitsService;

    public AdminController(DashboardService dashboardService, GovernorLimitsService governorLimitsService) {
        this.dashboardService = dashboardService;
        this.governorLimitsService = governorLimitsService;
    }

    /**
     * Returns tenant dashboard data including usage statistics and governor limits.
     * When a tenant context is set (via gateway), returns tenant-scoped usage and limits.
     * Otherwise falls back to system-level dashboard data.
     *
     * @param timeRange Time range for metrics (5m, 15m, 1h, 6h, 24h)
     * @return Dashboard data with limits and usage
     */
    @GetMapping("/dashboard")
    @Operation(
            summary = "Get dashboard data",
            description = "Returns tenant usage statistics and governor limits for the dashboard"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getDashboard(
            @Parameter(description = "Time range for metrics (5m, 15m, 1h, 6h, 24h)")
            @RequestParam(required = false, defaultValue = "15m") String timeRange) {

        String tenantId = TenantContextHolder.getTenantId();
        log.debug("REST request to get dashboard data - tenantId: {}, timeRange: {}", tenantId, timeRange);

        if (tenantId != null) {
            GovernorLimitsStatus status = governorLimitsService.getStatus(tenantId);
            GovernorLimits limits = status.limits();

            Map<String, Object> limitsMap = Map.of(
                    "apiCallsPerDay", limits.apiCallsPerDay(),
                    "storageGb", limits.storageGb(),
                    "maxUsers", limits.maxUsers(),
                    "maxCollections", limits.maxCollections(),
                    "maxFieldsPerCollection", limits.maxFieldsPerCollection(),
                    "maxWorkflows", limits.maxWorkflows(),
                    "maxReports", limits.maxReports()
            );

            Map<String, Object> usageMap = Map.of(
                    "apiCallsToday", status.apiCallsUsed(),
                    "storageUsedGb", 0,
                    "activeUsers", status.usersUsed(),
                    "collectionsCount", status.collectionsUsed()
            );

            return ResponseEntity.ok(Map.of("limits", limitsMap, "usage", usageMap));
        }

        Map<String, Object> dashboard = dashboardService.getDashboardData(timeRange);
        return ResponseEntity.ok(dashboard);
    }
}
