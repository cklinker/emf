package com.emf.controlplane.service;

import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WorkerMetricsService.
 * Tests metric computation for KEDA scaling and monitoring.
 */
@ExtendWith(MockitoExtension.class)
class WorkerMetricsServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private CollectionAssignmentRepository assignmentRepository;

    @Mock
    private WorkerRepository workerRepository;

    private WorkerMetricsService workerMetricsService;

    @BeforeEach
    void setUp() {
        workerMetricsService = new WorkerMetricsService(
                collectionRepository, assignmentRepository, workerRepository);
    }

    @Nested
    @DisplayName("getUnassignedCollectionCount")
    class GetUnassignedCollectionCountTests {

        @Test
        @DisplayName("should return count from repository")
        void shouldReturnCountFromRepository() {
            when(collectionRepository.countUnassignedCollections()).thenReturn(5L);

            long count = workerMetricsService.getUnassignedCollectionCount();

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("should return zero when all collections are assigned")
        void shouldReturnZeroWhenAllCollectionsAreAssigned() {
            when(collectionRepository.countUnassignedCollections()).thenReturn(0L);

            long count = workerMetricsService.getUnassignedCollectionCount();

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("should return total active count when no assignments exist")
        void shouldReturnTotalActiveCountWhenNoAssignmentsExist() {
            when(collectionRepository.countUnassignedCollections()).thenReturn(100L);

            long count = workerMetricsService.getUnassignedCollectionCount();

            assertThat(count).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("getWorkerStats")
    class GetWorkerStatsTests {

        @Test
        @DisplayName("should return correct stats with active workers")
        void shouldReturnCorrectStatsWithActiveWorkers() {
            when(workerRepository.count()).thenReturn(5L);
            when(workerRepository.countByStatus("READY")).thenReturn(3L);
            when(assignmentRepository.count()).thenReturn(120L);
            when(assignmentRepository.countByStatus("READY")).thenReturn(90L);
            when(collectionRepository.countUnassignedCollections()).thenReturn(10L);

            WorkerMetricsService.WorkerStats stats = workerMetricsService.getWorkerStats();

            assertThat(stats.totalWorkers()).isEqualTo(5L);
            assertThat(stats.readyWorkers()).isEqualTo(3L);
            assertThat(stats.totalAssignments()).isEqualTo(120L);
            assertThat(stats.readyAssignments()).isEqualTo(90L);
            assertThat(stats.unassignedCollections()).isEqualTo(10L);
            assertThat(stats.averageLoad()).isEqualTo(30.0); // 90 / 3 = 30.0
        }

        @Test
        @DisplayName("should return zero average load when no ready workers")
        void shouldReturnZeroAverageLoadWhenNoReadyWorkers() {
            when(workerRepository.count()).thenReturn(2L);
            when(workerRepository.countByStatus("READY")).thenReturn(0L);
            when(assignmentRepository.count()).thenReturn(50L);
            when(assignmentRepository.countByStatus("READY")).thenReturn(0L);
            when(collectionRepository.countUnassignedCollections()).thenReturn(25L);

            WorkerMetricsService.WorkerStats stats = workerMetricsService.getWorkerStats();

            assertThat(stats.totalWorkers()).isEqualTo(2L);
            assertThat(stats.readyWorkers()).isZero();
            assertThat(stats.averageLoad()).isZero();
            assertThat(stats.unassignedCollections()).isEqualTo(25L);
        }

        @Test
        @DisplayName("should return all zeros when system is empty")
        void shouldReturnAllZerosWhenSystemIsEmpty() {
            when(workerRepository.count()).thenReturn(0L);
            when(workerRepository.countByStatus("READY")).thenReturn(0L);
            when(assignmentRepository.count()).thenReturn(0L);
            when(assignmentRepository.countByStatus("READY")).thenReturn(0L);
            when(collectionRepository.countUnassignedCollections()).thenReturn(0L);

            WorkerMetricsService.WorkerStats stats = workerMetricsService.getWorkerStats();

            assertThat(stats.totalWorkers()).isZero();
            assertThat(stats.readyWorkers()).isZero();
            assertThat(stats.totalAssignments()).isZero();
            assertThat(stats.readyAssignments()).isZero();
            assertThat(stats.unassignedCollections()).isZero();
            assertThat(stats.averageLoad()).isZero();
        }

        @Test
        @DisplayName("should compute fractional average load")
        void shouldComputeFractionalAverageLoad() {
            when(workerRepository.count()).thenReturn(3L);
            when(workerRepository.countByStatus("READY")).thenReturn(3L);
            when(assignmentRepository.count()).thenReturn(10L);
            when(assignmentRepository.countByStatus("READY")).thenReturn(10L);
            when(collectionRepository.countUnassignedCollections()).thenReturn(0L);

            WorkerMetricsService.WorkerStats stats = workerMetricsService.getWorkerStats();

            // 10 / 3 = 3.333...
            assertThat(stats.averageLoad()).isCloseTo(3.333, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("should include non-ready workers in total count")
        void shouldIncludeNonReadyWorkersInTotalCount() {
            when(workerRepository.count()).thenReturn(5L);
            when(workerRepository.countByStatus("READY")).thenReturn(2L);
            when(assignmentRepository.count()).thenReturn(80L);
            when(assignmentRepository.countByStatus("READY")).thenReturn(40L);
            when(collectionRepository.countUnassignedCollections()).thenReturn(5L);

            WorkerMetricsService.WorkerStats stats = workerMetricsService.getWorkerStats();

            assertThat(stats.totalWorkers()).isEqualTo(5L);
            assertThat(stats.readyWorkers()).isEqualTo(2L);
            // Average load based only on READY workers: 40 / 2 = 20.0
            assertThat(stats.averageLoad()).isEqualTo(20.0);
        }
    }
}
