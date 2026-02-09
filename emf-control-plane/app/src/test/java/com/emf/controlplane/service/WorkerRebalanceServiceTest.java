package com.emf.controlplane.service;

import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.WorkerRepository;
import com.emf.runtime.event.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkerRebalanceService.
 * Tests the rebalancing algorithm including threshold detection,
 * assignment moves, and tenant affinity constraints.
 */
@ExtendWith(MockitoExtension.class)
class WorkerRebalanceServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private CollectionAssignmentRepository assignmentRepository;

    @Mock
    private ConfigEventPublisher eventPublisher;

    private WorkerRebalanceService rebalanceService;

    @BeforeEach
    void setUp() {
        rebalanceService = new WorkerRebalanceService(
                workerRepository, assignmentRepository, eventPublisher);
    }

    private Worker createWorker(String id, String status, int currentLoad, int capacity) {
        Worker worker = new Worker("host-" + id, 8080, "http://host-" + id + ":8080");
        worker.setId(id);
        worker.setStatus(status);
        worker.setPool("default");
        worker.setCurrentLoad(currentLoad);
        worker.setCapacity(capacity);
        worker.setLastHeartbeat(Instant.now());
        return worker;
    }

    private Worker createWorkerWithAffinity(String id, String tenantAffinity) {
        Worker worker = createWorker(id, "READY", 0, 50);
        worker.setTenantAffinity(tenantAffinity);
        return worker;
    }

    private CollectionAssignment createAssignment(String id, String collectionId,
            String workerId, String tenantId) {
        CollectionAssignment assignment = new CollectionAssignment(collectionId, workerId, tenantId);
        assignment.setId(id);
        assignment.setStatus("READY");
        return assignment;
    }

    @Nested
    @DisplayName("rebalance")
    class RebalanceTests {

        @Test
        @DisplayName("should skip when fewer than 2 READY workers")
        void shouldSkipWhenFewerThan2Workers() {
            when(workerRepository.findByStatus("READY"))
                    .thenReturn(List.of(createWorker("w1", "READY", 5, 50)));

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            assertThat(report.moveCount()).isZero();
            assertThat(report.workerCount()).isEqualTo(1);
            assertThat(report.moves()).isEmpty();
        }

        @Test
        @DisplayName("should skip when no READY workers")
        void shouldSkipWhenNoReadyWorkers() {
            when(workerRepository.findByStatus("READY"))
                    .thenReturn(Collections.emptyList());

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            assertThat(report.moveCount()).isZero();
            assertThat(report.workerCount()).isZero();
        }

        @Test
        @DisplayName("should not rebalance when distribution is balanced")
        void shouldNotRebalanceWhenDistributionIsBalanced() {
            Worker w1 = createWorker("w1", "READY", 5, 50);
            Worker w2 = createWorker("w2", "READY", 5, 50);

            when(workerRepository.findByStatus("READY")).thenReturn(List.of(w1, w2));
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(5L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(5L);

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            assertThat(report.moveCount()).isZero();
            assertThat(report.idealLoad()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("should move assignments from overloaded to underloaded worker")
        void shouldMoveAssignmentsFromOverloadedToUnderloaded() {
            Worker w1 = createWorker("w1", "READY", 10, 50);
            Worker w2 = createWorker("w2", "READY", 0, 50);

            when(workerRepository.findByStatus("READY")).thenReturn(List.of(w1, w2));
            // Initial counts
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(10L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L);

            // 10 total / 2 workers = 5.0 ideal
            // Overloaded threshold: ceil(5.0 * 1.2) = 6
            // Underloaded threshold: floor(5.0 * 0.8) = 4
            // w1 has 10 > 6: overloaded
            // w2 has 0 < 4: underloaded
            // Excess from w1: 10 - 5 = 5 assignments to move

            List<CollectionAssignment> w1Assignments = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                w1Assignments.add(createAssignment("a" + i, "c" + i, "w1", "default"));
            }

            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(w1Assignments);

            // After rebalance, re-count
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(10L, 5L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L, 5L);

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            assertThat(report.moveCount()).isEqualTo(5);
            assertThat(report.moves()).hasSize(5);
            assertThat(report.workerCount()).isEqualTo(2);

            // Verify assignments were saved
            verify(assignmentRepository, times(5)).save(any(CollectionAssignment.class));

            // Verify events were published for each move (2 events per move: DELETE from source, CREATE on target)
            verify(eventPublisher, times(5)).publishWorkerAssignmentChanged(
                    eq("w1"), any(), any(), any(), eq(ChangeType.DELETED));
            verify(eventPublisher, times(5)).publishWorkerAssignmentChanged(
                    eq("w2"), any(), any(), any(), eq(ChangeType.CREATED));
        }

        @Test
        @DisplayName("should skip rebalance when ideal load is less than 1")
        void shouldSkipWhenIdealLoadLessThan1() {
            Worker w1 = createWorker("w1", "READY", 0, 50);
            Worker w2 = createWorker("w2", "READY", 0, 50);
            Worker w3 = createWorker("w3", "READY", 0, 50);

            when(workerRepository.findByStatus("READY")).thenReturn(List.of(w1, w2, w3));
            when(assignmentRepository.countByWorkerIdAndStatus(any(), eq("READY"))).thenReturn(0L);

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            assertThat(report.moveCount()).isZero();
        }

        @Test
        @DisplayName("should return correct before and after distributions")
        void shouldReturnCorrectDistributions() {
            Worker w1 = createWorker("w1", "READY", 8, 50);
            Worker w2 = createWorker("w2", "READY", 0, 50);

            when(workerRepository.findByStatus("READY")).thenReturn(List.of(w1, w2));
            // First call for before, second for after
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(8L, 4L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY"))
                    .thenReturn(0L, 4L);

            List<CollectionAssignment> w1Assignments = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                w1Assignments.add(createAssignment("a" + i, "c" + i, "w1", "default"));
            }
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(w1Assignments);

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            assertThat(report.beforeDistribution()).containsEntry("w1", 8L);
            assertThat(report.beforeDistribution()).containsEntry("w2", 0L);
            assertThat(report.afterDistribution()).containsEntry("w1", 4L);
            assertThat(report.afterDistribution()).containsEntry("w2", 4L);
        }
    }

    @Nested
    @DisplayName("tenant affinity")
    class TenantAffinityTests {

        @Test
        @DisplayName("should respect tenant affinity when moving assignments")
        void shouldRespectTenantAffinityWhenMoving() {
            Worker w1 = createWorker("w1", "READY", 10, 50);
            Worker w2 = createWorkerWithAffinity("w2", "tenant-A");
            w2.setCurrentLoad(0);

            when(workerRepository.findByStatus("READY")).thenReturn(List.of(w1, w2));
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(10L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L);

            // Create assignments: some for tenant-A, some for tenant-B
            List<CollectionAssignment> w1Assignments = new ArrayList<>();
            // tenant-A assignments
            for (int i = 0; i < 5; i++) {
                w1Assignments.add(createAssignment("a" + i, "c" + i, "w1", "tenant-A"));
            }
            // tenant-B assignments
            for (int i = 5; i < 10; i++) {
                w1Assignments.add(createAssignment("a" + i, "c" + i, "w1", "tenant-B"));
            }
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(w1Assignments);

            // After rebalance
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(10L, 5L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L, 5L);

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            // Only tenant-A assignments should be moved to w2 (which has tenant-A affinity)
            // tenant-B assignments should NOT be moved to w2
            for (WorkerRebalanceService.RebalanceMove move : report.moves()) {
                if ("w2".equals(move.toWorkerId())) {
                    assertThat(move.tenantId()).isEqualTo("tenant-A");
                }
            }
        }

        @Test
        @DisplayName("should allow moving default tenant assignments to any worker")
        void shouldAllowMovingDefaultTenantAssignments() {
            Worker w1 = createWorker("w1", "READY", 8, 50);
            Worker w2 = createWorker("w2", "READY", 0, 50);

            when(workerRepository.findByStatus("READY")).thenReturn(List.of(w1, w2));
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(8L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L);

            List<CollectionAssignment> w1Assignments = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                w1Assignments.add(createAssignment("a" + i, "c" + i, "w1", "default"));
            }
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(w1Assignments);

            // After rebalance
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(8L, 4L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L, 4L);

            WorkerRebalanceService.RebalanceReport report = rebalanceService.rebalance();

            assertThat(report.moveCount()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("findSuitableTarget")
    class FindSuitableTargetTests {

        @Test
        @DisplayName("should return null when no underloaded workers available")
        void shouldReturnNullWhenNoUnderloadedWorkers() {
            CollectionAssignment assignment = createAssignment("a1", "c1", "w1", "default");
            Map<String, Long> currentCounts = new HashMap<>();
            Map<String, Worker> workerMap = new HashMap<>();

            Worker target = rebalanceService.findSuitableTarget(
                    assignment, Collections.emptyList(), currentCounts, 5.0, workerMap);

            assertThat(target).isNull();
        }

        @Test
        @DisplayName("should return null when all candidates are at ideal load")
        void shouldReturnNullWhenAllCandidatesAtIdealLoad() {
            Worker w2 = createWorker("w2", "READY", 5, 50);
            CollectionAssignment assignment = createAssignment("a1", "c1", "w1", "default");

            Map<String, Long> currentCounts = Map.of("w2", 5L);
            Map<String, Worker> workerMap = Map.of("w2", w2);

            Worker target = rebalanceService.findSuitableTarget(
                    assignment, List.of(w2), currentCounts, 5.0, workerMap);

            assertThat(target).isNull();
        }

        @Test
        @DisplayName("should skip worker with mismatched tenant affinity")
        void shouldSkipWorkerWithMismatchedTenantAffinity() {
            Worker w2 = createWorkerWithAffinity("w2", "tenant-A");
            CollectionAssignment assignment = createAssignment("a1", "c1", "w1", "tenant-B");

            Map<String, Long> currentCounts = Map.of("w2", 0L);
            Map<String, Worker> workerMap = Map.of("w2", w2);

            Worker target = rebalanceService.findSuitableTarget(
                    assignment, List.of(w2), currentCounts, 5.0, workerMap);

            assertThat(target).isNull();
        }

        @Test
        @DisplayName("should select worker with matching tenant affinity")
        void shouldSelectWorkerWithMatchingTenantAffinity() {
            Worker w2 = createWorkerWithAffinity("w2", "tenant-A");
            CollectionAssignment assignment = createAssignment("a1", "c1", "w1", "tenant-A");

            Map<String, Long> currentCounts = Map.of("w2", 0L);
            Map<String, Worker> workerMap = Map.of("w2", w2);

            Worker target = rebalanceService.findSuitableTarget(
                    assignment, List.of(w2), currentCounts, 5.0, workerMap);

            assertThat(target).isNotNull();
            assertThat(target.getId()).isEqualTo("w2");
        }

        @Test
        @DisplayName("should prefer affinity-matching worker when both available")
        void shouldPreferAffinityMatchingWorker() {
            Worker w2 = createWorker("w2", "READY", 0, 50); // no affinity
            Worker w3 = createWorkerWithAffinity("w3", "tenant-A");
            CollectionAssignment assignment = createAssignment("a1", "c1", "w1", "tenant-A");

            Map<String, Long> currentCounts = Map.of("w2", 0L, "w3", 0L);
            Map<String, Worker> workerMap = Map.of("w2", w2, "w3", w3);

            Worker target = rebalanceService.findSuitableTarget(
                    assignment, List.of(w2, w3), currentCounts, 5.0, workerMap);

            assertThat(target).isNotNull();
            assertThat(target.getId()).isEqualTo("w3");
        }
    }

    @Nested
    @DisplayName("RebalanceReport")
    class RebalanceReportTests {

        @Test
        @DisplayName("should create report with correct fields")
        void shouldCreateReportWithCorrectFields() {
            List<WorkerRebalanceService.RebalanceMove> moves = List.of(
                    new WorkerRebalanceService.RebalanceMove("c1", "t1", "w1", "w2"));
            Map<String, Long> before = Map.of("w1", 10L, "w2", 0L);
            Map<String, Long> after = Map.of("w1", 5L, "w2", 5L);

            WorkerRebalanceService.RebalanceReport report =
                    new WorkerRebalanceService.RebalanceReport(1, 2, 10, 5.0, moves, before, after);

            assertThat(report.moveCount()).isEqualTo(1);
            assertThat(report.workerCount()).isEqualTo(2);
            assertThat(report.totalAssignments()).isEqualTo(10);
            assertThat(report.idealLoad()).isEqualTo(5.0);
            assertThat(report.moves()).hasSize(1);
            assertThat(report.beforeDistribution()).containsEntry("w1", 10L);
            assertThat(report.afterDistribution()).containsEntry("w2", 5L);
        }
    }

    @Nested
    @DisplayName("event publishing")
    class EventPublishingTests {

        @Test
        @DisplayName("should work without event publisher")
        void shouldWorkWithoutEventPublisher() {
            WorkerRebalanceService serviceNoEvents = new WorkerRebalanceService(
                    workerRepository, assignmentRepository, null);

            Worker w1 = createWorker("w1", "READY", 10, 50);
            Worker w2 = createWorker("w2", "READY", 0, 50);

            when(workerRepository.findByStatus("READY")).thenReturn(List.of(w1, w2));
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(10L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L);

            List<CollectionAssignment> w1Assignments = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                w1Assignments.add(createAssignment("a" + i, "c" + i, "w1", "default"));
            }
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(w1Assignments);

            // After rebalance
            when(assignmentRepository.countByWorkerIdAndStatus("w1", "READY")).thenReturn(10L, 5L);
            when(assignmentRepository.countByWorkerIdAndStatus("w2", "READY")).thenReturn(0L, 5L);

            WorkerRebalanceService.RebalanceReport report = serviceNoEvents.rebalance();

            // Should still complete without errors
            assertThat(report.moveCount()).isGreaterThan(0);
        }
    }
}
