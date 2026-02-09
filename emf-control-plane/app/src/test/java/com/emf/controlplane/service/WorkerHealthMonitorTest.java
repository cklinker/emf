package com.emf.controlplane.service;

import com.emf.controlplane.entity.CollectionAssignment;
import com.emf.controlplane.entity.Worker;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.repository.CollectionAssignmentRepository;
import com.emf.controlplane.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkerHealthMonitor.
 * Tests heartbeat staleness detection, OFFLINE marking, and automatic reassignment.
 */
@ExtendWith(MockitoExtension.class)
class WorkerHealthMonitorTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private CollectionAssignmentRepository assignmentRepository;

    @Mock
    private CollectionAssignmentService collectionAssignmentService;

    @Mock
    private ConfigEventPublisher eventPublisher;

    private WorkerHealthMonitor healthMonitor;

    @BeforeEach
    void setUp() {
        healthMonitor = new WorkerHealthMonitor(
                workerRepository, assignmentRepository, collectionAssignmentService, eventPublisher);
    }

    private Worker createWorker(String id, String status, Instant lastHeartbeat) {
        Worker worker = new Worker("host-" + id, 8080, "http://host-" + id + ":8080");
        worker.setId(id);
        worker.setStatus(status);
        worker.setPool("default");
        worker.setLastHeartbeat(lastHeartbeat);
        return worker;
    }

    private CollectionAssignment createAssignment(String collectionId, String workerId, String status) {
        CollectionAssignment assignment = new CollectionAssignment(collectionId, workerId, "default");
        assignment.setStatus(status);
        return assignment;
    }

    @Nested
    @DisplayName("checkWorkerHealth")
    class CheckWorkerHealthTests {

        @Test
        @DisplayName("should skip when no active workers exist")
        void shouldSkipWhenNoActiveWorkers() {
            when(workerRepository.findByStatusIn(List.of("READY", "STARTING")))
                    .thenReturn(Collections.emptyList());

            healthMonitor.checkWorkerHealth();

            verify(workerRepository, never()).save(any());
            verify(eventPublisher, never()).publishWorkerStatusChanged(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should not mark healthy workers as offline")
        void shouldNotMarkHealthyWorkersOffline() {
            Worker healthyWorker = createWorker("w1", "READY", Instant.now());

            when(workerRepository.findByStatusIn(List.of("READY", "STARTING")))
                    .thenReturn(List.of(healthyWorker));

            healthMonitor.checkWorkerHealth();

            verify(workerRepository, never()).save(any());
            verify(eventPublisher, never()).publishWorkerStatusChanged(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should mark stale worker as OFFLINE")
        void shouldMarkStaleWorkerOffline() {
            Instant staleHeartbeat = Instant.now().minus(Duration.ofSeconds(60));
            Worker staleWorker = createWorker("w1", "READY", staleHeartbeat);

            when(workerRepository.findByStatusIn(List.of("READY", "STARTING")))
                    .thenReturn(List.of(staleWorker));
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(Collections.emptyList());
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());

            healthMonitor.checkWorkerHealth();

            ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);
            verify(workerRepository).save(workerCaptor.capture());
            assertThat(workerCaptor.getValue().getStatus()).isEqualTo("OFFLINE");

            verify(eventPublisher).publishWorkerStatusChanged("w1", "host-w1", "OFFLINE", "default");
        }

        @Test
        @DisplayName("should mark worker with null heartbeat as OFFLINE")
        void shouldMarkWorkerWithNullHeartbeatOffline() {
            Worker noHeartbeatWorker = createWorker("w1", "STARTING", null);

            when(workerRepository.findByStatusIn(List.of("READY", "STARTING")))
                    .thenReturn(List.of(noHeartbeatWorker));
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(Collections.emptyList());
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());

            healthMonitor.checkWorkerHealth();

            verify(workerRepository).save(any(Worker.class));
            verify(eventPublisher).publishWorkerStatusChanged("w1", "host-w1", "OFFLINE", "default");
        }

        @Test
        @DisplayName("should handle mix of healthy and stale workers")
        void shouldHandleMixOfHealthyAndStaleWorkers() {
            Worker healthyWorker = createWorker("w1", "READY", Instant.now());
            Worker staleWorker = createWorker("w2", "READY",
                    Instant.now().minus(Duration.ofSeconds(60)));

            when(workerRepository.findByStatusIn(List.of("READY", "STARTING")))
                    .thenReturn(List.of(healthyWorker, staleWorker));
            when(assignmentRepository.findByWorkerIdAndStatus("w2", "READY"))
                    .thenReturn(Collections.emptyList());
            when(assignmentRepository.findByWorkerIdAndStatus("w2", "PENDING"))
                    .thenReturn(Collections.emptyList());

            healthMonitor.checkWorkerHealth();

            // Only stale worker should be saved (marked OFFLINE)
            ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);
            verify(workerRepository, times(1)).save(workerCaptor.capture());
            assertThat(workerCaptor.getValue().getId()).isEqualTo("w2");
        }

        @Test
        @DisplayName("should trigger reassignment for stale worker with assignments")
        void shouldTriggerReassignmentForStaleWorkerWithAssignments() {
            Instant staleHeartbeat = Instant.now().minus(Duration.ofSeconds(60));
            Worker staleWorker = createWorker("w1", "READY", staleHeartbeat);

            CollectionAssignment readyAssignment = createAssignment("c1", "w1", "READY");
            CollectionAssignment pendingAssignment = createAssignment("c2", "w1", "PENDING");

            when(workerRepository.findByStatusIn(List.of("READY", "STARTING")))
                    .thenReturn(List.of(staleWorker));
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(List.of(readyAssignment));
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(List.of(pendingAssignment));

            healthMonitor.checkWorkerHealth();

            verify(collectionAssignmentService).reassignFromWorker("w1");
        }

        @Test
        @DisplayName("should not trigger reassignment when no assignments exist")
        void shouldNotTriggerReassignmentWhenNoAssignments() {
            Instant staleHeartbeat = Instant.now().minus(Duration.ofSeconds(60));
            Worker staleWorker = createWorker("w1", "READY", staleHeartbeat);

            when(workerRepository.findByStatusIn(List.of("READY", "STARTING")))
                    .thenReturn(List.of(staleWorker));
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(Collections.emptyList());
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());

            healthMonitor.checkWorkerHealth();

            verify(collectionAssignmentService, never()).reassignFromWorker(any());
        }
    }

    @Nested
    @DisplayName("isHeartbeatStale")
    class IsHeartbeatStaleTests {

        @Test
        @DisplayName("should return true for null heartbeat")
        void shouldReturnTrueForNullHeartbeat() {
            Worker worker = createWorker("w1", "READY", null);
            Instant threshold = Instant.now().minus(WorkerHealthMonitor.HEARTBEAT_STALENESS_THRESHOLD);

            assertThat(healthMonitor.isHeartbeatStale(worker, threshold)).isTrue();
        }

        @Test
        @DisplayName("should return true for heartbeat before threshold")
        void shouldReturnTrueForHeartbeatBeforeThreshold() {
            Instant oldHeartbeat = Instant.now().minus(Duration.ofMinutes(2));
            Worker worker = createWorker("w1", "READY", oldHeartbeat);
            Instant threshold = Instant.now().minus(WorkerHealthMonitor.HEARTBEAT_STALENESS_THRESHOLD);

            assertThat(healthMonitor.isHeartbeatStale(worker, threshold)).isTrue();
        }

        @Test
        @DisplayName("should return false for recent heartbeat")
        void shouldReturnFalseForRecentHeartbeat() {
            Worker worker = createWorker("w1", "READY", Instant.now());
            Instant threshold = Instant.now().minus(WorkerHealthMonitor.HEARTBEAT_STALENESS_THRESHOLD);

            assertThat(healthMonitor.isHeartbeatStale(worker, threshold)).isFalse();
        }
    }

    @Nested
    @DisplayName("handleStaleWorker")
    class HandleStaleWorkerTests {

        @Test
        @DisplayName("should mark worker OFFLINE and publish event")
        void shouldMarkWorkerOfflineAndPublishEvent() {
            Worker worker = createWorker("w1", "READY", Instant.now().minus(Duration.ofMinutes(2)));

            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(Collections.emptyList());
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());

            healthMonitor.handleStaleWorker(worker);

            assertThat(worker.getStatus()).isEqualTo("OFFLINE");
            verify(workerRepository).save(worker);
            verify(eventPublisher).publishWorkerStatusChanged("w1", "host-w1", "OFFLINE", "default");
        }

        @Test
        @DisplayName("should work without event publisher")
        void shouldWorkWithoutEventPublisher() {
            WorkerHealthMonitor monitorNoEvents = new WorkerHealthMonitor(
                    workerRepository, assignmentRepository, collectionAssignmentService, null);

            Worker worker = createWorker("w1", "READY", Instant.now().minus(Duration.ofMinutes(2)));

            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(Collections.emptyList());
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());

            monitorNoEvents.handleStaleWorker(worker);

            assertThat(worker.getStatus()).isEqualTo("OFFLINE");
            verify(workerRepository).save(worker);
        }
    }

    @Nested
    @DisplayName("reassignCollections")
    class ReassignCollectionsTests {

        @Test
        @DisplayName("should call reassignFromWorker when assignments exist")
        void shouldCallReassignFromWorkerWhenAssignmentsExist() {
            CollectionAssignment readyAssignment = createAssignment("c1", "w1", "READY");

            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(List.of(readyAssignment));
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());

            healthMonitor.reassignCollections("w1");

            verify(collectionAssignmentService).reassignFromWorker("w1");
        }

        @Test
        @DisplayName("should skip reassignment when no assignments exist")
        void shouldSkipReassignmentWhenNoAssignments() {
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(Collections.emptyList());
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());

            healthMonitor.reassignCollections("w1");

            verify(collectionAssignmentService, never()).reassignFromWorker(any());
        }

        @Test
        @DisplayName("should mark remaining assignments PENDING when reassignment fails")
        void shouldMarkRemainingAssignmentsPendingWhenReassignmentFails() {
            CollectionAssignment assignment = createAssignment("c1", "w1", "READY");

            when(assignmentRepository.findByWorkerIdAndStatus("w1", "READY"))
                    .thenReturn(List.of(assignment))
                    .thenReturn(List.of(assignment)); // Called again in markRemainingAssignmentsPending
            when(assignmentRepository.findByWorkerIdAndStatus("w1", "PENDING"))
                    .thenReturn(Collections.emptyList());
            doThrow(new RuntimeException("No workers available"))
                    .when(collectionAssignmentService).reassignFromWorker("w1");

            healthMonitor.reassignCollections("w1");

            ArgumentCaptor<CollectionAssignment> captor = ArgumentCaptor.forClass(CollectionAssignment.class);
            verify(assignmentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        }
    }
}
