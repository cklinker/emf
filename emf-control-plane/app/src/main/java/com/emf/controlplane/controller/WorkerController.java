package com.emf.controlplane.controller;

import com.emf.controlplane.dto.WorkerHeartbeatRequest;
import com.emf.controlplane.dto.WorkerRegistrationRequest;
import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.service.CollectionAssignmentService;
import com.emf.controlplane.service.WorkerRebalanceService;
import com.emf.controlplane.service.WorkerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing worker registrations, heartbeats, and lifecycle.
 * Provides endpoints for worker registration, heartbeat processing, and status queries.
 */
@RestController
@RequestMapping("/control/workers")
public class WorkerController {

    private final WorkerService workerService;
    private final CollectionAssignmentService collectionAssignmentService;
    private final WorkerRebalanceService workerRebalanceService;

    public WorkerController(
            WorkerService workerService,
            CollectionAssignmentService collectionAssignmentService,
            WorkerRebalanceService workerRebalanceService) {
        this.workerService = workerService;
        this.collectionAssignmentService = collectionAssignmentService;
        this.workerRebalanceService = workerRebalanceService;
    }

    /**
     * Registers a new worker or re-registers an existing one.
     *
     * @param request The registration request
     * @return The registered worker
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Worker> register(@RequestBody WorkerRegistrationRequest request) {
        Worker worker = workerService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(worker);
    }

    /**
     * Processes a heartbeat from a worker.
     *
     * @param id The worker ID
     * @param request The heartbeat request
     * @return The updated worker
     */
    @PostMapping("/{id}/heartbeat")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Worker> heartbeat(
            @PathVariable String id,
            @RequestBody WorkerHeartbeatRequest request) {
        Worker worker = workerService.heartbeat(id, request);
        return ResponseEntity.ok(worker);
    }

    /**
     * Initiates graceful shutdown of a worker.
     *
     * @param id The worker ID
     * @return No content
     */
    @PostMapping("/{id}/deregister")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deregister(@PathVariable String id) {
        workerService.deregister(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all workers.
     *
     * @return List of all workers
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Worker>> listWorkers() {
        List<Worker> workers = workerService.findAll();
        return ResponseEntity.ok(workers);
    }

    /**
     * Gets a worker by ID.
     *
     * @param id The worker ID
     * @return The worker
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Worker> getWorker(@PathVariable String id) {
        Worker worker = workerService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", id));
        return ResponseEntity.ok(worker);
    }

    /**
     * Gets collection assignments for a worker.
     *
     * @param id The worker ID
     * @return List of assignments for the worker
     */
    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CollectionAssignment>> getWorkerAssignments(@PathVariable String id) {
        List<CollectionAssignment> assignments = collectionAssignmentService.findByWorker(id);
        return ResponseEntity.ok(assignments);
    }

    /**
     * Triggers a manual rebalance of collection assignments across workers.
     * Calculates the ideal distribution and moves assignments from overloaded
     * to underloaded workers, respecting tenant affinity constraints.
     *
     * @return The rebalance report with details of moves made
     */
    @PostMapping("/rebalance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkerRebalanceService.RebalanceReport> rebalance() {
        WorkerRebalanceService.RebalanceReport report = workerRebalanceService.rebalance();
        return ResponseEntity.ok(report);
    }
}
