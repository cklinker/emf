package io.kelta.worker.service;

import io.kelta.worker.repository.FlowLogRetentionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("FlowLogRetentionSweep Tests")
class FlowLogRetentionSweepTest {

    private FlowLogRetentionRepository repository;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        repository = mock(FlowLogRetentionRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        when(repository.countDueExecutions(any())).thenReturn(0L);
        when(repository.countDueJobLogs(any())).thenReturn(0L);
    }

    private FlowLogRetentionSweep sweep(boolean enabled, boolean dryRun) {
        return sweep(enabled, dryRun, 60, 1000, 20);
    }

    private FlowLogRetentionSweep sweep(boolean enabled, boolean dryRun,
                                        int maxAgeDays, int batchSize, int maxBatches) {
        return new FlowLogRetentionSweep(repository, meterRegistry,
                enabled, dryRun, maxAgeDays, batchSize, maxBatches);
    }

    private double purgedCount() {
        return meterRegistry.get("kelta_worker_flowlog_purged").counter().count();
    }

    @Nested
    @DisplayName("Gating")
    class Gating {

        @Test
        @DisplayName("disabled sweep touches nothing")
        void disabledTouchesNothing() {
            sweep(false, false).sweep();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("DRY-RUN is the default posture and deletes nothing")
        void dryRunDeletesNothing() {
            when(repository.countDueExecutions(any())).thenReturn(12_431L);
            when(repository.countDueJobLogs(any())).thenReturn(3_010L);

            sweep(true, true).sweep();

            // Counted, reported, and NOT deleted.
            verify(repository).countDueExecutions(any());
            verify(repository).countDueJobLogs(any());
            verify(repository, never()).deleteExecutionBatch(any(), anyInt());
            verify(repository, never()).deleteJobLogBatch(any(), anyInt());
            assertThat(purgedCount()).isZero();
        }

        @Test
        @DisplayName("nothing due short-circuits before any delete")
        void nothingDueShortCircuits() {
            sweep(true, false).sweep();

            verify(repository, never()).deleteExecutionBatch(any(), anyInt());
            verify(repository, never()).deleteJobLogBatch(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("Armed deletion")
    class ArmedDeletion {

        @Test
        @DisplayName("deletes both tables and counts the rows")
        void deletesBothTables() {
            when(repository.countDueExecutions(any())).thenReturn(5L);
            when(repository.countDueJobLogs(any())).thenReturn(3L);
            when(repository.deleteExecutionBatch(any(), anyInt())).thenReturn(5);
            when(repository.deleteJobLogBatch(any(), anyInt())).thenReturn(3);

            sweep(true, false, 60, 1000, 20).sweep();

            verify(repository).deleteExecutionBatch(any(), anyInt());
            verify(repository).deleteJobLogBatch(any(), anyInt());
            assertThat(purgedCount()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("keeps draining while batches come back full")
        void drainsFullBatches() {
            when(repository.countDueExecutions(any())).thenReturn(250L);
            // Two full batches, then a short one ends the drain.
            when(repository.deleteExecutionBatch(any(), anyInt()))
                    .thenReturn(100, 100, 50);

            sweep(true, false, 60, 100, 20).purgeExecutions(Instant.now());

            verify(repository, times(3)).deleteExecutionBatch(any(), anyInt());
            assertThat(purgedCount()).isEqualTo(250.0);
        }

        @Test
        @DisplayName("stops at the per-cycle batch cap rather than running unbounded")
        void respectsBatchCap() {
            when(repository.countDueExecutions(any())).thenReturn(1_000_000L);
            // Always a full batch: without the cap this would never terminate.
            when(repository.deleteExecutionBatch(any(), anyInt())).thenReturn(100);

            sweep(true, false, 60, 100, 3).purgeExecutions(Instant.now());

            verify(repository, times(3)).deleteExecutionBatch(any(), anyInt());
            assertThat(purgedCount()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("passes the configured batch size through")
        void passesBatchSize() {
            when(repository.countDueExecutions(any())).thenReturn(10L);
            when(repository.deleteExecutionBatch(any(), anyInt())).thenReturn(10);

            sweep(true, false, 60, 500, 20).purgeExecutions(Instant.now());

            verify(repository).deleteExecutionBatch(any(), org.mockito.ArgumentMatchers.eq(500));
        }
    }

    @Nested
    @DisplayName("Cutoff")
    class Cutoff {

        @Test
        @DisplayName("derives the cutoff from max-age-days")
        void derivesCutoff() {
            when(repository.countDueExecutions(any())).thenReturn(1L);
            when(repository.deleteExecutionBatch(any(), anyInt())).thenReturn(1);
            Instant before = Instant.now().minus(Duration.ofDays(30));

            sweep(true, false, 30, 1000, 20).sweep();

            ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
            verify(repository).countDueExecutions(cutoff.capture());
            // Within a few seconds of "30 days ago".
            assertThat(Duration.between(before, cutoff.getValue()).abs())
                    .isLessThan(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("both tables are swept against the same cutoff")
        void sameCutoffForBothTables() {
            when(repository.countDueExecutions(any())).thenReturn(1L);
            when(repository.countDueJobLogs(any())).thenReturn(1L);
            when(repository.deleteExecutionBatch(any(), anyInt())).thenReturn(1);
            when(repository.deleteJobLogBatch(any(), anyInt())).thenReturn(1);

            sweep(true, false).sweep();

            ArgumentCaptor<Instant> execCutoff = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> jobCutoff = ArgumentCaptor.forClass(Instant.class);
            verify(repository).countDueExecutions(execCutoff.capture());
            verify(repository).countDueJobLogs(jobCutoff.capture());
            assertThat(execCutoff.getValue()).isEqualTo(jobCutoff.getValue());
        }
    }

    @Nested
    @DisplayName("Failure isolation")
    class FailureIsolation {

        @Test
        @DisplayName("an execution-phase failure still lets job logs be pruned")
        void executionFailureDoesNotBlockJobLogs() {
            when(repository.countDueExecutions(any()))
                    .thenThrow(new IllegalStateException("lock timeout"));
            when(repository.countDueJobLogs(any())).thenReturn(2L);
            when(repository.deleteJobLogBatch(any(), anyInt())).thenReturn(2);

            sweep(true, false).sweep();

            verify(repository).deleteJobLogBatch(any(), anyInt());
        }

        @Test
        @DisplayName("a failure never escapes to the scheduler")
        void failureDoesNotEscape() {
            when(repository.countDueExecutions(any()))
                    .thenThrow(new IllegalStateException("db down"));
            when(repository.countDueJobLogs(any()))
                    .thenThrow(new IllegalStateException("db down"));

            // A thrown exception would suppress every future run of a fixedDelay task.
            sweep(true, false).sweep();
        }
    }
}
