package com.emf.controlplane.service;

import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for computing worker-related metrics used by Kubernetes autoscaling.
 * Provides data for KEDA scalers and HPA custom metrics.
 */
@Service
public class WorkerMetricsService {

    private static final Logger log = LoggerFactory.getLogger(WorkerMetricsService.class);

    private final CollectionRepository collectionRepository;
    private final CollectionAssignmentRepository assignmentRepository;
    private final WorkerRepository workerRepository;

    public WorkerMetricsService(
            CollectionRepository collectionRepository,
            CollectionAssignmentRepository assignmentRepository,
            WorkerRepository workerRepository) {
        this.collectionRepository = collectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.workerRepository = workerRepository;
    }

    /**
     * Counts active collections that have no READY assignment.
     * Used by KEDA to determine if additional workers need to be scaled up.
     *
     * @return the number of unassigned collections
     */
    @Transactional(readOnly = true)
    public long getUnassignedCollectionCount() {
        long count = collectionRepository.countUnassignedCollections();
        log.debug("Unassigned collection count: {}", count);
        return count;
    }

    /**
     * Computes aggregate worker statistics for monitoring and autoscaling decisions.
     *
     * @return a snapshot of current worker statistics
     */
    @Transactional(readOnly = true)
    public WorkerStats getWorkerStats() {
        long totalWorkers = workerRepository.count();
        long readyWorkers = workerRepository.countByStatus("READY");
        long totalAssignments = assignmentRepository.count();
        long readyAssignments = assignmentRepository.countByStatus("READY");
        long unassignedCollections = collectionRepository.countUnassignedCollections();

        double averageLoad = readyWorkers > 0
                ? (double) readyAssignments / readyWorkers
                : 0.0;

        log.debug("Worker stats: totalWorkers={}, readyWorkers={}, totalAssignments={}, " +
                  "readyAssignments={}, unassignedCollections={}, averageLoad={}",
                totalWorkers, readyWorkers, totalAssignments, readyAssignments,
                unassignedCollections, averageLoad);

        return new WorkerStats(totalWorkers, readyWorkers, totalAssignments,
                readyAssignments, unassignedCollections, averageLoad);
    }

    /**
     * Immutable snapshot of worker statistics.
     *
     * @param totalWorkers total number of registered workers (all statuses)
     * @param readyWorkers number of workers in READY status
     * @param totalAssignments total number of collection assignments (all statuses)
     * @param readyAssignments number of assignments in READY status
     * @param unassignedCollections number of active collections without a READY assignment
     * @param averageLoad average number of READY assignments per READY worker
     */
    public record WorkerStats(
            long totalWorkers,
            long readyWorkers,
            long totalAssignments,
            long readyAssignments,
            long unassignedCollections,
            double averageLoad
    ) {}
}
