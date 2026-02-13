package com.emf.controlplane.service;

import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.WorkerRepository;
import com.emf.runtime.event.ChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for rebalancing collection assignments across workers.
 * Calculates the ideal distribution and moves assignments from overloaded
 * workers to underloaded workers, respecting tenant affinity constraints.
 *
 * <p>Can be triggered manually via REST endpoint or runs automatically
 * every 5 minutes.
 */
@Service
public class WorkerRebalanceService {

    private static final Logger log = LoggerFactory.getLogger(WorkerRebalanceService.class);

    /**
     * Threshold above ideal load percentage that considers a worker overloaded.
     */
    static final double OVERLOADED_THRESHOLD = 1.20;

    /**
     * Threshold below ideal load percentage that considers a worker underloaded.
     */
    static final double UNDERLOADED_THRESHOLD = 0.80;

    private final WorkerRepository workerRepository;
    private final CollectionAssignmentRepository assignmentRepository;
    private final ConfigEventPublisher eventPublisher;

    public WorkerRebalanceService(
            WorkerRepository workerRepository,
            CollectionAssignmentRepository assignmentRepository,
            @Nullable ConfigEventPublisher eventPublisher) {
        this.workerRepository = workerRepository;
        this.assignmentRepository = assignmentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Scheduled rebalance that runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledRebalance() {
        log.debug("Running scheduled worker rebalance");
        rebalance();
    }

    /**
     * Performs rebalancing of collection assignments across all READY workers.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Find all READY workers</li>
     *   <li>Count total active (READY) assignments</li>
     *   <li>Calculate ideal distribution: total / number of workers</li>
     *   <li>Identify overloaded workers ({@literal >}120% of ideal) and underloaded workers ({@literal <}80% of ideal)</li>
     *   <li>Move assignments from overloaded to underloaded, respecting tenant affinity</li>
     *   <li>Publish assignment changed events for each move</li>
     * </ol>
     *
     * @return A rebalance report describing the actions taken
     */
    @Transactional
    public RebalanceReport rebalance() {
        List<Worker> readyWorkers = workerRepository.findByStatus("READY");

        if (readyWorkers.size() < 2) {
            log.info("Rebalance skipped: fewer than 2 READY workers (found {})", readyWorkers.size());
            return new RebalanceReport(0, readyWorkers.size(), 0, 0.0,
                    Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
        }

        // Build a map of workerId -> active assignment count
        Map<String, Long> beforeDistribution = new LinkedHashMap<>();
        for (Worker worker : readyWorkers) {
            long count = assignmentRepository.countByWorkerIdAndStatus(worker.getId(), "READY");
            beforeDistribution.put(worker.getId(), count);
        }

        long totalAssignments = beforeDistribution.values().stream().mapToLong(Long::longValue).sum();
        double idealLoad = (double) totalAssignments / readyWorkers.size();

        if (idealLoad < 1.0) {
            log.info("Rebalance skipped: ideal load ({}) is less than 1 per worker", idealLoad);
            return new RebalanceReport(0, readyWorkers.size(), totalAssignments, idealLoad,
                    Collections.emptyList(), beforeDistribution, beforeDistribution);
        }

        long overloadedThreshold = (long) Math.ceil(idealLoad * OVERLOADED_THRESHOLD);
        long underloadedThreshold = (long) Math.floor(idealLoad * UNDERLOADED_THRESHOLD);

        // Identify overloaded and underloaded workers
        List<Worker> overloaded = new ArrayList<>();
        List<Worker> underloaded = new ArrayList<>();

        for (Worker worker : readyWorkers) {
            long count = beforeDistribution.get(worker.getId());
            if (count > overloadedThreshold) {
                overloaded.add(worker);
            } else if (count < underloadedThreshold) {
                underloaded.add(worker);
            }
        }

        if (overloaded.isEmpty() || underloaded.isEmpty()) {
            log.info("Rebalance not needed: overloaded={}, underloaded={}, ideal={}",
                    overloaded.size(), underloaded.size(), idealLoad);
            return new RebalanceReport(0, readyWorkers.size(), totalAssignments, idealLoad,
                    Collections.emptyList(), beforeDistribution, beforeDistribution);
        }

        log.info("Rebalancing: {} overloaded, {} underloaded workers, ideal load={}",
                overloaded.size(), underloaded.size(), idealLoad);

        // Build a map of workerId -> Worker for quick lookup
        Map<String, Worker> workerMap = readyWorkers.stream()
                .collect(Collectors.toMap(Worker::getId, w -> w));

        // Track current assignment counts (mutable)
        Map<String, Long> currentCounts = new LinkedHashMap<>(beforeDistribution);

        // Perform moves
        List<RebalanceMove> moves = new ArrayList<>();

        // Sort overloaded by count descending (most overloaded first)
        overloaded.sort((a, b) -> Long.compare(
                currentCounts.getOrDefault(b.getId(), 0L),
                currentCounts.getOrDefault(a.getId(), 0L)));

        // Sort underloaded by count ascending (most underloaded first)
        underloaded.sort(Comparator.comparingLong(w -> currentCounts.getOrDefault(w.getId(), 0L)));

        for (Worker source : overloaded) {
            long sourceCount = currentCounts.getOrDefault(source.getId(), 0L);
            long idealLong = Math.round(idealLoad);
            long excess = sourceCount - idealLong;

            if (excess <= 0) {
                continue;
            }

            // Get moveable assignments from the overloaded worker
            List<CollectionAssignment> sourceAssignments =
                    assignmentRepository.findByWorkerIdAndStatus(source.getId(), "READY");

            for (CollectionAssignment assignment : sourceAssignments) {
                if (excess <= 0) {
                    break;
                }

                // Find a suitable underloaded target
                Worker target = findSuitableTarget(assignment, underloaded, currentCounts, idealLoad, workerMap);

                if (target == null) {
                    continue;
                }

                // Perform the move
                moveAssignment(assignment, source, target);
                moves.add(new RebalanceMove(
                        assignment.getCollectionId(),
                        assignment.getTenantId(),
                        source.getId(),
                        target.getId()));

                // Update counts
                currentCounts.merge(source.getId(), -1L, Long::sum);
                currentCounts.merge(target.getId(), 1L, Long::sum);
                excess--;

                // Re-sort underloaded since counts changed
                underloaded.sort(Comparator.comparingLong(w -> currentCounts.getOrDefault(w.getId(), 0L)));
            }
        }

        // Build after distribution
        Map<String, Long> afterDistribution = new LinkedHashMap<>();
        for (Worker worker : readyWorkers) {
            long count = assignmentRepository.countByWorkerIdAndStatus(worker.getId(), "READY");
            afterDistribution.put(worker.getId(), count);
        }

        log.info("Rebalance complete: {} move(s) performed", moves.size());

        return new RebalanceReport(
                moves.size(),
                readyWorkers.size(),
                totalAssignments,
                idealLoad,
                moves,
                beforeDistribution,
                afterDistribution);
    }

    /**
     * Finds a suitable underloaded target worker for a given assignment,
     * respecting tenant affinity constraints.
     *
     * @param assignment The assignment to move
     * @param underloaded The list of underloaded workers
     * @param currentCounts Current assignment counts per worker
     * @param idealLoad The ideal assignment count per worker
     * @param workerMap Map of workerId to Worker for lookup
     * @return A suitable target worker, or null if none found
     */
    Worker findSuitableTarget(
            CollectionAssignment assignment,
            List<Worker> underloaded,
            Map<String, Long> currentCounts,
            double idealLoad,
            Map<String, Worker> workerMap) {

        String tenantId = assignment.getTenantId();
        long idealLong = Math.round(idealLoad);

        for (Worker candidate : underloaded) {
            long candidateCount = currentCounts.getOrDefault(candidate.getId(), 0L);

            // Don't push a target above ideal
            if (candidateCount >= idealLong) {
                continue;
            }

            // Respect tenant affinity: if the assignment has a tenant ID and the candidate
            // has a tenant affinity set, they must match
            if (candidate.getTenantAffinity() != null && !candidate.getTenantAffinity().isBlank()) {
                if (tenantId != null && !tenantId.equals(candidate.getTenantAffinity())) {
                    continue;
                }
            }

            // Also check: if the source worker had tenant affinity matching this assignment,
            // don't move it to a worker without matching affinity
            // (i.e., preserve tenant affinity assignments)
            if (tenantId != null && !tenantId.isBlank() && !"default".equals(tenantId)) {
                // Check if there are any workers with matching tenant affinity
                boolean affinityWorkersExist = underloaded.stream()
                        .anyMatch(w -> tenantId.equals(w.getTenantAffinity()));

                if (affinityWorkersExist && !tenantId.equals(candidate.getTenantAffinity())) {
                    // There are affinity-matching workers available, so skip this non-matching one
                    continue;
                }
            }

            return candidate;
        }

        return null;
    }

    /**
     * Moves an assignment from the source worker to the target worker.
     * Updates the assignment's workerId and timestamps, adjusts worker load counts,
     * and publishes assignment changed events.
     *
     * @param assignment The assignment to move
     * @param source The source worker (overloaded)
     * @param target The target worker (underloaded)
     */
    private void moveAssignment(CollectionAssignment assignment, Worker source, Worker target) {
        log.info("Moving assignment: collectionId={}, tenantId={}, from={}, to={}",
                assignment.getCollectionId(), assignment.getTenantId(),
                source.getId(), target.getId());

        // Update assignment
        assignment.setWorkerId(target.getId());
        assignment.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment);

        // Update source worker load
        source.setCurrentLoad(Math.max(0, source.getCurrentLoad() - 1));
        workerRepository.save(source);

        // Update target worker load
        target.setCurrentLoad(target.getCurrentLoad() + 1);
        workerRepository.save(target);

        // Publish assignment changed events
        if (eventPublisher != null) {
            eventPublisher.publishWorkerAssignmentChanged(
                    source.getId(), assignment.getCollectionId(),
                    source.getBaseUrl(), assignment.getCollectionId(), ChangeType.DELETED);
            eventPublisher.publishWorkerAssignmentChanged(
                    target.getId(), assignment.getCollectionId(),
                    target.getBaseUrl(), assignment.getCollectionId(), ChangeType.CREATED);
        }
    }

    /**
     * Report of a rebalance operation.
     */
    public record RebalanceReport(
            int moveCount,
            int workerCount,
            long totalAssignments,
            double idealLoad,
            List<RebalanceMove> moves,
            Map<String, Long> beforeDistribution,
            Map<String, Long> afterDistribution) {
    }

    /**
     * Represents a single assignment move during rebalancing.
     */
    public record RebalanceMove(
            String collectionId,
            String tenantId,
            String fromWorkerId,
            String toWorkerId) {
    }
}
