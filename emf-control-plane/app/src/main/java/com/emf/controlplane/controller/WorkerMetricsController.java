package com.emf.controlplane.controller;

import com.emf.controlplane.service.WorkerMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for worker metrics used by Kubernetes autoscaling.
 * Provides endpoints for KEDA scalers and monitoring dashboards.
 *
 * <p>These endpoints are permitted without authentication (configured in SecurityConfig)
 * so that KEDA and Prometheus can scrape them from within the cluster.
 */
@RestController
@RequestMapping("/control/metrics")
@PreAuthorize("@securityService.hasPermission(#root, 'VIEW_SETUP')")
public class WorkerMetricsController {

    private final WorkerMetricsService workerMetricsService;

    public WorkerMetricsController(WorkerMetricsService workerMetricsService) {
        this.workerMetricsService = workerMetricsService;
    }

    /**
     * Returns the count of active collections that have no READY assignment.
     * Used by KEDA to determine if additional worker pods should be scaled up.
     *
     * @return JSON object with unassignedCount field
     */
    @GetMapping("/unassigned-collections")
    public ResponseEntity<Map<String, Long>> getUnassignedCollections() {
        long count = workerMetricsService.getUnassignedCollectionCount();
        return ResponseEntity.ok(Map.of("unassignedCount", count));
    }

    /**
     * Returns aggregate worker statistics for monitoring and autoscaling decisions.
     *
     * @return JSON object with worker statistics
     */
    @GetMapping("/worker-stats")
    public ResponseEntity<WorkerMetricsService.WorkerStats> getWorkerStats() {
        WorkerMetricsService.WorkerStats stats = workerMetricsService.getWorkerStats();
        return ResponseEntity.ok(stats);
    }
}
