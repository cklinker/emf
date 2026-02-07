package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateDashboardRequest;
import com.emf.controlplane.dto.UserDashboardDto;
import com.emf.controlplane.service.UserDashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/dashboards")
public class UserDashboardController {

    private final UserDashboardService dashboardService;

    public UserDashboardController(UserDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<UserDashboardDto> listDashboards(
            @RequestParam String tenantId,
            @RequestParam(required = false) String userId) {
        return dashboardService.listDashboards(tenantId, userId).stream()
                .map(UserDashboardDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public UserDashboardDto getDashboard(@PathVariable String id) {
        return UserDashboardDto.fromEntity(dashboardService.getDashboard(id));
    }

    @PostMapping
    public ResponseEntity<UserDashboardDto> createDashboard(
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody CreateDashboardRequest request) {
        var dashboard = dashboardService.createDashboard(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDashboardDto.fromEntity(dashboard));
    }

    @PutMapping("/{id}")
    public UserDashboardDto updateDashboard(
            @PathVariable String id,
            @RequestBody CreateDashboardRequest request) {
        return UserDashboardDto.fromEntity(dashboardService.updateDashboard(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDashboard(@PathVariable String id) {
        dashboardService.deleteDashboard(id);
        return ResponseEntity.noContent().build();
    }
}
