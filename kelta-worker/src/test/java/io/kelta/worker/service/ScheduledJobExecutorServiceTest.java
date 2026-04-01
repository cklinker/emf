package io.kelta.worker.service;

import tools.jackson.databind.ObjectMapper;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.flow.FlowEngine;
import io.kelta.runtime.flow.InitialStateBuilder;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("ScheduledJobExecutorService")
class ScheduledJobExecutorServiceTest {

    private ScheduledJobRepository repository;
    private FlowEngine flowEngine;
    private InitialStateBuilder initialStateBuilder;
    private ObjectMapper objectMapper;
    private ScheduledJobExecutorService executor;

    @BeforeEach
    void setUp() {
        repository = mock(ScheduledJobRepository.class);
        flowEngine = mock(FlowEngine.class);
        initialStateBuilder = new InitialStateBuilder();
        objectMapper = new ObjectMapper();
        executor = new ScheduledJobExecutorService(repository, flowEngine, initialStateBuilder, objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("executeAll")
    class ExecuteAll {

        @Test
        @DisplayName("Should execute due FLOW job via FlowEngine")
        void shouldExecuteDueFlowJob() {
            Map<String, Object> job = Map.of(
                    "id", "job-1", "tenant_id", "t1", "job_type", "FLOW",
                    "job_reference_id", "flow-1", "cron_expression", "0 0 * * * *", "timezone", "UTC");
            when(repository.findDueJobs()).thenReturn(List.of(job));
            when(repository.findFlowById("flow-1")).thenReturn(Optional.of(Map.of(
                    "id", "flow-1", "tenant_id", "t1", "definition", "{\"StartAt\":\"Start\"}",
                    "trigger_config", "{}", "active", true)));
            when(flowEngine.startExecution(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                    .thenReturn("exec-1");

            executor.executeAll();

            verify(flowEngine).startExecution(eq("t1"), eq("flow-1"), anyString(), any(), isNull(), eq(false));
            verify(repository).updateAfterExecution(eq("job-1"), eq("SUCCESS"), any(), any());
            verify(repository).insertExecutionLog(eq("job-1"), eq("SUCCESS"), isNull(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Should mark FAILED when flow not found")
        void shouldFailWhenFlowNotFound() {
            Map<String, Object> job = Map.of(
                    "id", "job-2", "tenant_id", "t1", "job_type", "FLOW",
                    "job_reference_id", "missing-flow", "cron_expression", "0 0 * * * *", "timezone", "UTC");
            when(repository.findDueJobs()).thenReturn(List.of(job));
            when(repository.findFlowById("missing-flow")).thenReturn(Optional.empty());

            executor.executeAll();

            verify(flowEngine, never()).startExecution(any(), any(), any(), any(), any(), anyBoolean());
            verify(repository).updateAfterExecution(eq("job-2"), eq("FAILED"), any(), any());
            verify(repository).insertExecutionLog(eq("job-2"), eq("FAILED"), contains("not found"), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Should mark FAILED when flow is inactive")
        void shouldFailWhenFlowInactive() {
            Map<String, Object> job = Map.of(
                    "id", "job-3", "tenant_id", "t1", "job_type", "FLOW",
                    "job_reference_id", "flow-2", "cron_expression", "0 0 * * * *", "timezone", "UTC");
            when(repository.findDueJobs()).thenReturn(List.of(job));
            when(repository.findFlowById("flow-2")).thenReturn(Optional.of(Map.of(
                    "id", "flow-2", "active", false, "definition", "{}", "trigger_config", "{}")));

            executor.executeAll();

            verify(flowEngine, never()).startExecution(any(), any(), any(), any(), any(), anyBoolean());
            verify(repository).updateAfterExecution(eq("job-3"), eq("FAILED"), any(), any());
        }

        @Test
        @DisplayName("Should process all jobs even when one fails")
        void shouldContinueOnFailure() {
            Map<String, Object> job1 = Map.of(
                    "id", "j1", "tenant_id", "t1", "job_type", "FLOW",
                    "job_reference_id", "missing", "cron_expression", "0 0 * * * *", "timezone", "UTC");
            Map<String, Object> job2 = Map.of(
                    "id", "j2", "tenant_id", "t1", "job_type", "FLOW",
                    "job_reference_id", "flow-ok", "cron_expression", "0 0 * * * *", "timezone", "UTC");
            when(repository.findDueJobs()).thenReturn(List.of(job1, job2));
            when(repository.findFlowById("missing")).thenReturn(Optional.empty());
            when(repository.findFlowById("flow-ok")).thenReturn(Optional.of(Map.of(
                    "id", "flow-ok", "active", true, "definition", "{}", "trigger_config", "{}")));
            when(flowEngine.startExecution(any(), any(), any(), any(), any(), anyBoolean())).thenReturn("exec-2");

            executor.executeAll();

            // Both jobs processed — first failed, second succeeded
            verify(repository).updateAfterExecution(eq("j1"), eq("FAILED"), any(), any());
            verify(repository).updateAfterExecution(eq("j2"), eq("SUCCESS"), any(), any());
        }

        @Test
        @DisplayName("Should skip unsupported job types and recalculate next_run_at")
        void shouldSkipUnsupportedJobType() {
            Map<String, Object> job = Map.of(
                    "id", "j-script", "tenant_id", "t1", "job_type", "SCRIPT",
                    "job_reference_id", "x", "cron_expression", "0 0 * * * *", "timezone", "UTC");
            when(repository.findDueJobs()).thenReturn(List.of(job));

            executor.executeAll();

            verify(flowEngine, never()).startExecution(any(), any(), any(), any(), any(), anyBoolean());
            verify(repository).updateAfterExecution(eq("j-script"), eq("SKIPPED"), any(), any());
        }

        @Test
        @DisplayName("Should do nothing when no due jobs")
        void shouldDoNothingWhenNoDueJobs() {
            when(repository.findDueJobs()).thenReturn(List.of());

            executor.executeAll();

            verify(flowEngine, never()).startExecution(any(), any(), any(), any(), any(), anyBoolean());
            verify(repository, never()).updateAfterExecution(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should clear TenantContext even when exception thrown")
        void shouldClearTenantContextOnException() {
            Map<String, Object> job = Map.of(
                    "id", "j-err", "tenant_id", "t-err", "job_type", "FLOW",
                    "job_reference_id", "flow-err", "cron_expression", "0 0 * * * *", "timezone", "UTC");
            when(repository.findDueJobs()).thenReturn(List.of(job));
            when(repository.findFlowById("flow-err")).thenReturn(Optional.of(Map.of(
                    "id", "flow-err", "active", true, "definition", "{}", "trigger_config", "{}")));
            when(flowEngine.startExecution(any(), any(), any(), any(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException("Boom"));

            executor.executeAll();

            // TenantContext should be cleared after the exception
            assertThat(TenantContext.get()).isNull();
        }
    }

    @Nested
    @DisplayName("calculateNextRunAt")
    class CalculateNextRunAt {

        @Test
        @DisplayName("Should calculate next run in specified timezone")
        void shouldCalculateInTimezone() {
            Instant next = ScheduledJobRepository.calculateNextRunAt("0 0 9 * * *", "America/New_York");
            assertThat(next).isNotNull().isAfter(Instant.now());
        }

        @Test
        @DisplayName("Should default to UTC when timezone is null")
        void shouldDefaultToUtc() {
            Instant next = ScheduledJobRepository.calculateNextRunAt("0 0 9 * * *", null);
            assertThat(next).isNotNull().isAfter(Instant.now());
        }

        @Test
        @DisplayName("Should calculate from NOW, not from stale time")
        void shouldCalculateFromNow() {
            // Two calls at roughly the same time should produce similar results
            Instant next1 = ScheduledJobRepository.calculateNextRunAt("0 0 * * * *", "UTC");
            Instant next2 = ScheduledJobRepository.calculateNextRunAt("0 0 * * * *", "UTC");
            // Both should be in the future
            assertThat(next1).isAfter(Instant.now().minusSeconds(1));
            assertThat(next2).isAfter(Instant.now().minusSeconds(1));
        }
    }
}
