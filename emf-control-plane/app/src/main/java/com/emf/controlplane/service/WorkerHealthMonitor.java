package com.emf.controlplane.service;

import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled service that monitors worker health via heartbeat staleness.
 * Runs every 30 seconds, checks all READY or STARTING workers, and marks
 * workers as OFFLINE if their last heartbeat is older than 45 seconds.
 * When a worker goes OFFLINE, its collection assignments are automatically
 * reassigned to other available workers.
 */
@Service
public class WorkerHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(WorkerHealthMonitor.class);

    /**
     * Maximum age of a heartbeat before a worker is considered stale.
     */
    static final Duration HEARTBEAT_STALENESS_THRESHOLD = Duration.ofSeconds(45);

    private final WorkerRepository workerRepository;
    private final CollectionAssignmentRepository assignmentRepository;
    private final CollectionAssignmentService collectionAssignmentService;
    private final ConfigEventPublisher eventPublisher;

    public WorkerHealthMonitor(
            WorkerRepository workerRepository,
            CollectionAssignmentRepository assignmentRepository,
            CollectionAssignmentService collectionAssignmentService,
            @Nullable ConfigEventPublisher eventPublisher) {
        this.workerRepository = workerRepository;
        this.assignmentRepository = assignmentRepository;
        this.collectionAssignmentService = collectionAssignmentService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Scheduled health check that runs every 30 seconds.
     * Queries all workers with status READY or STARTING, checks their lastHeartbeat,
     * and marks stale workers as OFFLINE with automatic reassignment.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkWorkerHealth() {
        log.debug("Running worker health check");

        List<Worker> activeWorkers = workerRepository.findByStatusIn(List.of("READY", "STARTING"));

        if (activeWorkers.isEmpty()) {
            log.debug("No active workers to check");
            return;
        }

        Instant threshold = Instant.now().minus(HEARTBEAT_STALENESS_THRESHOLD);
        int staleCount = 0;

        for (Worker worker : activeWorkers) {
            if (isHeartbeatStale(worker, threshold)) {
                staleCount++;
                handleStaleWorker(worker);
            }
        }

        if (staleCount > 0) {
            log.info("Worker health check complete: {} stale worker(s) marked OFFLINE", staleCount);
        } else {
            log.debug("Worker health check complete: all {} worker(s) healthy", activeWorkers.size());
        }
    }

    /**
     * Checks whether a worker's heartbeat is stale.
     *
     * @param worker The worker to check
     * @param threshold The staleness threshold time
     * @return true if the heartbeat is stale or missing
     */
    boolean isHeartbeatStale(Worker worker, Instant threshold) {
        if (worker.getLastHeartbeat() == null) {
            log.warn("Worker has no heartbeat recorded: workerId={}, status={}",
                    worker.getId(), worker.getStatus());
            return true;
        }
        return worker.getLastHeartbeat().isBefore(threshold);
    }

    /**
     * Handles a stale worker by marking it OFFLINE, publishing a status event,
     * and triggering reassignment of its collection assignments.
     *
     * @param worker The stale worker
     */
    void handleStaleWorker(Worker worker) {
        log.warn("Worker heartbeat stale: workerId={}, host={}, lastHeartbeat={}, status={}",
                worker.getId(), worker.getHost(), worker.getLastHeartbeat(), worker.getStatus());

        // Mark worker as OFFLINE
        String previousStatus = worker.getStatus();
        worker.setStatus("OFFLINE");
        workerRepository.save(worker);

        // Publish status changed event
        if (eventPublisher != null) {
            eventPublisher.publishWorkerStatusChanged(
                    worker.getId(), worker.getHost(), "OFFLINE", worker.getPool());
        }

        log.info("Worker marked OFFLINE: workerId={}, previousStatus={}", worker.getId(), previousStatus);

        // Reassign collections from the offline worker
        reassignCollections(worker.getId());
    }

    /**
     * Reassigns all active assignments from a failed worker.
     * For each READY assignment, attempts to reassign to another worker via
     * {@link CollectionAssignmentService#reassignFromWorker}. If no workers
     * are available, assignments are left as PENDING for future pickup.
     *
     * @param workerId The worker ID whose assignments need to be reassigned
     */
    void reassignCollections(String workerId) {
        List<CollectionAssignment> readyAssignments =
                assignmentRepository.findByWorkerIdAndStatus(workerId, "READY");
        List<CollectionAssignment> pendingAssignments =
                assignmentRepository.findByWorkerIdAndStatus(workerId, "PENDING");

        int totalAssignments = readyAssignments.size() + pendingAssignments.size();

        if (totalAssignments == 0) {
            log.info("No active assignments to reassign from worker: workerId={}", workerId);
            return;
        }

        log.info("Reassigning {} assignment(s) from offline worker: workerId={} ({} READY, {} PENDING)",
                totalAssignments, workerId, readyAssignments.size(), pendingAssignments.size());

        try {
            collectionAssignmentService.reassignFromWorker(workerId);
        } catch (Exception e) {
            log.error("Error during reassignment from worker {}: {}", workerId, e.getMessage(), e);

            // Mark any remaining active assignments as PENDING so they can be picked up later
            markRemainingAssignmentsPending(workerId);
        }
    }

    /**
     * Marks any remaining READY assignments for a worker as PENDING.
     * This is a fallback when reassignment fails, ensuring assignments
     * will be picked up when a new worker becomes available.
     *
     * @param workerId The worker ID
     */
    private void markRemainingAssignmentsPending(String workerId) {
        List<CollectionAssignment> remaining =
                assignmentRepository.findByWorkerIdAndStatus(workerId, "READY");

        for (CollectionAssignment assignment : remaining) {
            log.warn("Marking assignment as PENDING (no available workers): collectionId={}, workerId={}",
                    assignment.getCollectionId(), workerId);
            assignment.setStatus("PENDING");
            assignmentRepository.save(assignment);
        }
    }
}
