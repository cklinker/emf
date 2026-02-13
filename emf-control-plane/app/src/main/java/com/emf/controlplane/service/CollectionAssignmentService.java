package com.emf.controlplane.service;

import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.WorkerRepository;
import com.emf.runtime.event.ChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing collection-to-worker assignments.
 * Handles assignment, unassignment, and reassignment of collections across workers.
 */
@Service
public class CollectionAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(CollectionAssignmentService.class);

    private final CollectionAssignmentRepository assignmentRepository;
    private final WorkerRepository workerRepository;
    private final CollectionRepository collectionRepository;
    private final ConfigEventPublisher eventPublisher;
    private final ControlPlaneProperties properties;

    public CollectionAssignmentService(
            CollectionAssignmentRepository assignmentRepository,
            WorkerRepository workerRepository,
            CollectionRepository collectionRepository,
            @Nullable ConfigEventPublisher eventPublisher,
            ControlPlaneProperties properties) {
        this.assignmentRepository = assignmentRepository;
        this.workerRepository = workerRepository;
        this.collectionRepository = collectionRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Assigns a collection to the best available worker.
     * <p>
     * Assignment algorithm:
     * 1. Find workers where status = 'READY' and currentLoad < capacity
     * 2. If collection has tenant, prefer workers with matching tenantAffinity
     * 3. Sort by currentLoad ascending (least loaded first)
     * 4. Pick first worker
     * 5. If no candidates, throw IllegalStateException
     *
     * @param collectionId The collection ID to assign
     * @param tenantId The tenant ID for the collection
     * @return The created assignment
     */
    @Transactional
    public CollectionAssignment assignCollection(String collectionId, String tenantId) {
        log.info("Assigning collection: collectionId={}, tenantId={}", collectionId, tenantId);

        // Find available workers (READY status with available capacity)
        List<Worker> readyWorkers = workerRepository.findByStatus("READY");
        List<Worker> candidates = readyWorkers.stream()
                .filter(w -> w.getCurrentLoad() < w.getCapacity())
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No available workers");
        }

        // Prefer workers with matching tenant affinity
        if (tenantId != null && !tenantId.isBlank()) {
            List<Worker> affinityMatches = candidates.stream()
                    .filter(w -> tenantId.equals(w.getTenantAffinity()))
                    .collect(Collectors.toList());
            if (!affinityMatches.isEmpty()) {
                candidates = affinityMatches;
            }
        }

        // Sort by current load ascending (least loaded first)
        candidates.sort(Comparator.comparingInt(Worker::getCurrentLoad));

        // Pick the first (least loaded) worker
        Worker selectedWorker = candidates.get(0);

        // Create the assignment
        CollectionAssignment assignment = new CollectionAssignment(collectionId, selectedWorker.getId(),
                tenantId != null ? tenantId : "default");
        assignment.setStatus("PENDING");
        assignment = assignmentRepository.save(assignment);

        // Increment worker's current load
        selectedWorker.setCurrentLoad(selectedWorker.getCurrentLoad() + 1);
        workerRepository.save(selectedWorker);

        // Publish assignment event using worker's pod-specific URL for direct routing
        String collectionName = collectionRepository.findById(collectionId)
                .map(c -> c.getName())
                .orElse(collectionId);
        publishAssignmentEvent(selectedWorker.getId(), collectionId,
                selectedWorker.getBaseUrl(), collectionName, ChangeType.CREATED);

        log.info("Collection assigned: collectionId={}, workerId={}", collectionId, selectedWorker.getId());
        return assignment;
    }

    /**
     * Removes an assignment by setting its status to REMOVED.
     *
     * @param assignmentId The assignment ID
     */
    @Transactional
    public void unassignCollection(String assignmentId) {
        log.info("Unassigning collection: assignmentId={}", assignmentId);

        CollectionAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("CollectionAssignment", assignmentId));

        assignment.setStatus("REMOVED");
        assignmentRepository.save(assignment);

        // Decrement worker's current load
        workerRepository.findById(assignment.getWorkerId()).ifPresent(worker -> {
            worker.setCurrentLoad(Math.max(0, worker.getCurrentLoad() - 1));
            workerRepository.save(worker);
        });

        // Publish assignment removal event
        String collectionName = collectionRepository.findById(assignment.getCollectionId())
                .map(c -> c.getName())
                .orElse(assignment.getCollectionId());
        String workerBaseUrl = workerRepository.findById(assignment.getWorkerId())
                .map(Worker::getBaseUrl)
                .orElse(properties.getWorkerServiceUrl());
        publishAssignmentEvent(assignment.getWorkerId(), assignment.getCollectionId(),
                workerBaseUrl, collectionName, ChangeType.DELETED);

        log.info("Collection unassigned: assignmentId={}", assignmentId);
    }

    /**
     * Finds all assignments for a worker.
     *
     * @param workerId The worker ID
     * @return List of assignments for the worker
     */
    @Transactional(readOnly = true)
    public List<CollectionAssignment> findByWorker(String workerId) {
        return assignmentRepository.findByWorkerId(workerId);
    }

    /**
     * Finds all assignments for a collection.
     *
     * @param collectionId The collection ID
     * @return List of assignments for the collection
     */
    @Transactional(readOnly = true)
    public List<CollectionAssignment> findByCollection(String collectionId) {
        return assignmentRepository.findByCollectionId(collectionId);
    }

    /**
     * Marks a collection assignment as READY.
     * Called by a worker after it has fully loaded the collection.
     *
     * @param collectionId The collection ID
     * @param workerId The worker ID
     */
    @Transactional
    public void markReady(String collectionId, String workerId) {
        log.info("Marking assignment as READY: collectionId={}, workerId={}", collectionId, workerId);

        CollectionAssignment assignment = assignmentRepository.findByCollectionIdAndWorkerId(collectionId, workerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CollectionAssignment", collectionId + "/" + workerId));

        assignment.setStatus("READY");
        assignment.setReadyAt(Instant.now());
        assignmentRepository.save(assignment);

        log.info("Assignment marked as READY: collectionId={}, workerId={}", collectionId, workerId);
    }

    /**
     * Reassigns all READY and PENDING collections from a worker to other available workers.
     * Used when a worker is being drained or has gone offline.
     *
     * @param workerId The worker ID to reassign from
     */
    @Transactional
    public void reassignFromWorker(String workerId) {
        log.info("Reassigning collections from worker: workerId={}", workerId);

        List<CollectionAssignment> activeAssignments = assignmentRepository.findByWorkerIdAndStatus(workerId, "READY");
        activeAssignments.addAll(assignmentRepository.findByWorkerIdAndStatus(workerId, "PENDING"));

        for (CollectionAssignment assignment : activeAssignments) {
            assignment.setStatus("REMOVED");
            assignmentRepository.save(assignment);

            // Try to reassign to another worker
            try {
                assignCollection(assignment.getCollectionId(), assignment.getTenantId());
                log.info("Reassigned collection: collectionId={}", assignment.getCollectionId());
            } catch (IllegalStateException e) {
                log.warn("Could not reassign collection {}: {}", assignment.getCollectionId(), e.getMessage());
            }
        }

        log.info("Completed reassignment from worker: workerId={}, collections={}", workerId, activeAssignments.size());
    }

    /**
     * Finds all assignments.
     *
     * @return List of all assignments
     */
    @Transactional(readOnly = true)
    public List<CollectionAssignment> findAll() {
        return assignmentRepository.findAll();
    }

    /**
     * Publishes a worker assignment changed event to Kafka.
     */
    private void publishAssignmentEvent(String workerId, String collectionId,
            String workerBaseUrl, String collectionName, ChangeType changeType) {
        if (eventPublisher != null) {
            eventPublisher.publishWorkerAssignmentChanged(
                    workerId, collectionId, workerBaseUrl, collectionName, changeType);
        } else {
            log.debug("Event publishing disabled - assignment changed: workerId={}, collectionId={}",
                    workerId, collectionId);
        }
    }
}
