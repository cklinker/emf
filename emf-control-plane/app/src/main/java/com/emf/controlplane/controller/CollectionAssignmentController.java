package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AssignmentRequest;
import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.service.CollectionAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing collection-to-worker assignments.
 * Provides endpoints for assigning, unassigning, and querying collection assignments.
 */
@RestController
@RequestMapping("/control/assignments")
public class CollectionAssignmentController {

    private final CollectionAssignmentService assignmentService;

    public CollectionAssignmentController(CollectionAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Manually assigns a collection to an available worker.
     *
     * @param request The assignment request containing collectionId and tenantId
     * @return The created assignment
     */
    @PostMapping("/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CollectionAssignment> assign(@RequestBody AssignmentRequest request) {
        CollectionAssignment assignment = assignmentService.assignCollection(
                request.collectionId(), request.tenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    /**
     * Removes a collection assignment.
     *
     * @param id The assignment ID
     * @return No content
     */
    @PostMapping("/unassign/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unassign(@PathVariable String id) {
        assignmentService.unassignCollection(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all collection assignments.
     *
     * @return List of all assignments
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CollectionAssignment>> listAssignments() {
        List<CollectionAssignment> assignments = assignmentService.findAll();
        return ResponseEntity.ok(assignments);
    }

    /**
     * Marks a collection assignment as READY.
     * Called by a worker after it has fully loaded a collection.
     *
     * @param collectionId The collection ID
     * @param workerId The worker ID
     * @return No content
     */
    @PostMapping("/{collectionId}/{workerId}/ready")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> markReady(
            @PathVariable String collectionId,
            @PathVariable String workerId) {
        assignmentService.markReady(collectionId, workerId);
        return ResponseEntity.noContent().build();
    }
}
