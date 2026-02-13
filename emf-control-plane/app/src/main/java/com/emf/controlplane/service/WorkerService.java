package com.emf.controlplane.service;

import com.emf.controlplane.dto.WorkerHeartbeatRequest;
import com.emf.controlplane.dto.WorkerRegistrationRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.WorkerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing worker registrations, heartbeats, and lifecycle.
 * Workers are runtime processes that host and serve collections.
 */
@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final WorkerRepository workerRepository;
    private final CollectionRepository collectionRepository;
    private final CollectionAssignmentRepository assignmentRepository;
    private final CollectionAssignmentService collectionAssignmentService;
    private final ConfigEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public WorkerService(
            WorkerRepository workerRepository,
            CollectionRepository collectionRepository,
            CollectionAssignmentRepository assignmentRepository,
            @Nullable CollectionAssignmentService collectionAssignmentService,
            @Nullable ConfigEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.workerRepository = workerRepository;
        this.collectionRepository = collectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.collectionAssignmentService = collectionAssignmentService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a new worker or re-registers an existing one.
     * If a worker with the same host already exists, it is updated.
     *
     * @param request The registration request
     * @return The registered worker
     */
    @Transactional
    public Worker register(WorkerRegistrationRequest request) {
        log.info("Registering worker: host={}, port={}, pool={}", request.host(), request.port(), request.pool());

        // Check for re-registration by host
        Optional<Worker> existing = workerRepository.findByHost(request.host());
        Worker worker;

        if (existing.isPresent()) {
            worker = existing.get();
            log.info("Re-registering existing worker: id={}", worker.getId());
        } else if (request.workerId() != null && !request.workerId().isBlank()) {
            // Check if a worker with the specified ID exists
            worker = workerRepository.findById(request.workerId()).orElseGet(Worker::new);
            if (request.workerId() != null && !request.workerId().isBlank()) {
                worker.setId(request.workerId());
            }
        } else {
            worker = new Worker();
        }

        worker.setHost(request.host());
        worker.setPort(request.port());
        worker.setBaseUrl("http://" + request.host() + ":" + request.port());
        worker.setPodName(request.podName());
        worker.setNamespace(request.namespace());
        worker.setCapacity(request.capacity() > 0 ? request.capacity() : 50);
        worker.setPool(request.pool() != null ? request.pool() : "default");
        worker.setTenantAffinity(request.tenantAffinity());
        worker.setStatus("STARTING");
        worker.setLastHeartbeat(Instant.now());

        // Serialize labels to JSON
        if (request.labels() != null && !request.labels().isEmpty()) {
            try {
                worker.setLabels(objectMapper.writeValueAsString(request.labels()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize worker labels", e);
            }
        }

        worker = workerRepository.save(worker);

        // Publish worker status changed event
        publishWorkerStatusChanged(worker);

        log.info("Worker registered: id={}, host={}", worker.getId(), worker.getHost());
        return worker;
    }

    /**
     * Processes a heartbeat from a worker.
     * Updates the worker's last heartbeat time, current load, and status.
     *
     * @param workerId The worker ID
     * @param request The heartbeat request
     * @return The updated worker
     */
    @Transactional
    public Worker heartbeat(String workerId, WorkerHeartbeatRequest request) {
        log.debug("Heartbeat from worker: id={}", workerId);

        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        worker.setLastHeartbeat(Instant.now());
        worker.setCurrentLoad(request.currentLoad());

        if (request.status() != null && !request.status().isBlank()) {
            String previousStatus = worker.getStatus();
            worker.setStatus(request.status());

            // Publish event if status changed
            if (!request.status().equals(previousStatus)) {
                worker = workerRepository.save(worker);
                publishWorkerStatusChanged(worker);

                // When a worker transitions to READY, assign any unassigned collections
                if ("READY".equals(request.status()) && !"READY".equals(previousStatus)) {
                    assignUnassignedCollections();
                }

                return worker;
            }
        }

        worker = workerRepository.save(worker);
        return worker;
    }

    /**
     * Deregisters a worker, setting its status to DRAINING.
     * This triggers reassignment of collections from this worker.
     *
     * @param workerId The worker ID
     */
    @Transactional
    public void deregister(String workerId) {
        log.info("Deregistering worker: id={}", workerId);

        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        worker.setStatus("DRAINING");
        workerRepository.save(worker);

        // Publish worker status changed event
        publishWorkerStatusChanged(worker);

        log.info("Worker set to DRAINING: id={}", workerId);
    }

    /**
     * Finds a worker by ID.
     *
     * @param id The worker ID
     * @return The worker if found
     */
    @Transactional(readOnly = true)
    public Optional<Worker> findById(String id) {
        return workerRepository.findById(id);
    }

    /**
     * Finds all workers.
     *
     * @return List of all workers
     */
    @Transactional(readOnly = true)
    public List<Worker> findAll() {
        return workerRepository.findAll();
    }

    /**
     * Finds workers by status.
     *
     * @param status The status to filter by
     * @return List of workers with the given status
     */
    @Transactional(readOnly = true)
    public List<Worker> findByStatus(String status) {
        return workerRepository.findByStatus(status);
    }

    /**
     * Marks a worker as OFFLINE.
     *
     * @param workerId The worker ID
     */
    @Transactional
    public void markOffline(String workerId) {
        log.info("Marking worker as OFFLINE: id={}", workerId);

        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        worker.setStatus("OFFLINE");
        workerRepository.save(worker);

        // Publish worker status changed event
        publishWorkerStatusChanged(worker);

        log.info("Worker marked as OFFLINE: id={}", workerId);
    }

    /**
     * Finds active collections that have no active (PENDING or READY) worker assignment
     * and assigns them to available workers.
     */
    private void assignUnassignedCollections() {
        if (collectionAssignmentService == null) {
            return;
        }

        try {
            List<Collection> activeCollections = collectionRepository.findByActiveTrue();
            for (Collection collection : activeCollections) {
                List<CollectionAssignment> assignments = assignmentRepository.findByCollectionId(collection.getId());
                boolean hasActive = assignments.stream()
                        .anyMatch(a -> "READY".equals(a.getStatus()) || "PENDING".equals(a.getStatus()));
                if (!hasActive) {
                    try {
                        collectionAssignmentService.assignCollection(collection.getId(), collection.getTenantId());
                        log.info("Auto-assigned unassigned collection: {}", collection.getName());
                    } catch (IllegalStateException e) {
                        log.warn("Could not auto-assign collection {}: {}", collection.getName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error during auto-assignment of unassigned collections: {}", e.getMessage());
        }
    }

    /**
     * Publishes a worker status changed event to Kafka.
     *
     * @param worker The worker whose status changed
     */
    private void publishWorkerStatusChanged(Worker worker) {
        if (eventPublisher != null) {
            eventPublisher.publishWorkerStatusChanged(
                    worker.getId(), worker.getHost(), worker.getStatus(), worker.getPool());
        } else {
            log.debug("Event publishing disabled - worker status changed: {}", worker.getId());
        }
    }
}
