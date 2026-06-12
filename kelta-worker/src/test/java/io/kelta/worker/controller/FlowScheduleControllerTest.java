package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.FlowRepository;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.junit.jupiter.api.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("FlowScheduleController")
class FlowScheduleControllerTest {

    private FlowRepository flowRepository;
    private ScheduledJobRepository scheduledJobRepository;
    private FlowScheduleController controller;

    @BeforeEach
    void setUp() {
        flowRepository = mock(FlowRepository.class);
        scheduledJobRepository = mock(ScheduledJobRepository.class);
        controller = new FlowScheduleController(flowRepository, scheduledJobRepository);
        TenantContext.set("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("GET /api/flows/{id}/schedule")
    class GetSchedule {

        @Test
        @DisplayName("Returns SYNCED with cron fields for a registered SCHEDULED flow")
        @SuppressWarnings("unchecked")
        void returnsSyncedForRegisteredFlow() {
            Instant last = Instant.parse("2026-06-10T09:00:00Z");
            Instant next = Instant.parse("2026-06-13T09:00:00Z");
            when(flowRepository.findFlowTypeForTenant("flow-1", "t1"))
                    .thenReturn(Optional.of(Map.of("id", "flow-1", "flow_type", "SCHEDULED", "active", true)));
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("id", "job-1");
            job.put("cron_expression", "0 0 9 * * *");
            job.put("timezone", "UTC");
            job.put("active", true);
            job.put("last_run_at", Timestamp.from(last));
            job.put("last_status", "SUCCESS");
            job.put("next_run_at", Timestamp.from(next));
            when(scheduledJobRepository.findScheduleForFlow("flow-1", "t1")).thenReturn(Optional.of(job));

            var response = controller.getSchedule("flow-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            assertThat(attrs).containsEntry("scheduleStatus", "SYNCED");
            assertThat(attrs).containsEntry("cron", "0 0 9 * * *");
            assertThat(attrs).containsEntry("timezone", "UTC");
            assertThat(attrs).containsEntry("active", true);
            assertThat(attrs).containsEntry("lastStatus", "SUCCESS");
            assertThat(attrs.get("lastRunAt")).isEqualTo(last.toString());
            assertThat(attrs.get("nextRunAt")).isEqualTo(next.toString());
            assertThat(attrs).doesNotContainKey("reason");
        }

        @Test
        @DisplayName("Returns UNSYNCED + reason when SCHEDULED flow has no scheduled_job row")
        @SuppressWarnings("unchecked")
        void returnsUnsyncedWhenNoJobRow() {
            when(flowRepository.findFlowTypeForTenant("flow-2", "t1"))
                    .thenReturn(Optional.of(Map.of("id", "flow-2", "flow_type", "SCHEDULED")));
            when(scheduledJobRepository.findScheduleForFlow("flow-2", "t1")).thenReturn(Optional.empty());

            var response = controller.getSchedule("flow-2");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            assertThat(attrs).containsEntry("scheduleStatus", "UNSYNCED");
            assertThat((String) attrs.get("reason")).contains("no scheduled_job row");
            assertThat(attrs.get("cron")).isNull();
            assertThat(attrs.get("nextRunAt")).isNull();
        }

        @Test
        @DisplayName("Returns UNSYNCED + reason when scheduled_job has next_run_at=NULL")
        @SuppressWarnings("unchecked")
        void returnsUnsyncedWhenNextRunAtNull() {
            when(flowRepository.findFlowTypeForTenant("flow-3", "t1"))
                    .thenReturn(Optional.of(Map.of("id", "flow-3", "flow_type", "SCHEDULED")));
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("id", "job-3");
            job.put("cron_expression", "0 0 9 * * *");
            job.put("timezone", "UTC");
            job.put("active", false);
            job.put("last_run_at", null);
            job.put("last_status", null);
            job.put("next_run_at", null);
            when(scheduledJobRepository.findScheduleForFlow("flow-3", "t1")).thenReturn(Optional.of(job));

            var response = controller.getSchedule("flow-3");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            assertThat(attrs).containsEntry("scheduleStatus", "UNSYNCED");
            assertThat((String) attrs.get("reason")).contains("next_run_at");
            assertThat(attrs).containsEntry("cron", "0 0 9 * * *");
            assertThat(attrs).containsEntry("active", false);
        }

        @Test
        @DisplayName("Returns NOT_SCHEDULED for an AUTOLAUNCHED flow")
        @SuppressWarnings("unchecked")
        void returnsNotScheduledForAutolaunched() {
            when(flowRepository.findFlowTypeForTenant("flow-4", "t1"))
                    .thenReturn(Optional.of(Map.of("id", "flow-4", "flow_type", "AUTOLAUNCHED")));
            when(scheduledJobRepository.findScheduleForFlow("flow-4", "t1")).thenReturn(Optional.empty());

            var response = controller.getSchedule("flow-4");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            assertThat(attrs).containsEntry("scheduleStatus", "NOT_SCHEDULED");
            assertThat(attrs).doesNotContainKey("reason");
        }

        @Test
        @DisplayName("Returns 404 for an unknown flow")
        void returns404ForUnknownFlow() {
            when(flowRepository.findFlowTypeForTenant("nope", "t1")).thenReturn(Optional.empty());

            var response = controller.getSchedule("nope");

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(scheduledJobRepository);
        }

        @Test
        @DisplayName("Returns 404 for a flow belonging to another tenant")
        void returns404ForOtherTenant() {
            when(flowRepository.findFlowTypeForTenant("flow-other", "t1")).thenReturn(Optional.empty());

            var response = controller.getSchedule("flow-other");

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("Returns 400 when there is no tenant context")
        void returns400WhenNoTenant() {
            TenantContext.clear();

            var response = controller.getSchedule("flow-1");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(flowRepository, scheduledJobRepository);
        }
    }

    @Nested
    @DisplayName("GET /api/flows/{id}/runs")
    class GetRuns {

        @Test
        @DisplayName("Returns recent runs for a known flow")
        @SuppressWarnings("unchecked")
        void returnsRecentRuns() {
            Instant started = Instant.parse("2026-06-12T08:00:00Z");
            Instant completed = Instant.parse("2026-06-12T08:00:05Z");
            when(flowRepository.findFlowTypeForTenant("flow-1", "t1"))
                    .thenReturn(Optional.of(Map.of("id", "flow-1", "flow_type", "SCHEDULED")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "log-1");
            row.put("status", "SUCCESS");
            row.put("started_at", Timestamp.from(started));
            row.put("completed_at", Timestamp.from(completed));
            row.put("duration_ms", 5000);
            row.put("error_message", null);
            when(scheduledJobRepository.findRecentRunsForFlow(eq("flow-1"), eq("t1"), anyInt()))
                    .thenReturn(List.of(row));

            var response = controller.getRuns("flow-1", 50);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            assertThat(data).hasSize(1);
            Map<String, Object> attrs = (Map<String, Object>) data.get(0).get("attributes");
            assertThat(attrs).containsEntry("status", "SUCCESS");
            assertThat(attrs).containsEntry("durationMs", 5000);
            assertThat(attrs.get("startedAt")).isEqualTo(started.toString());
            assertThat(attrs.get("completedAt")).isEqualTo(completed.toString());
            assertThat(attrs.get("errorMessage")).isNull();
        }

        @Test
        @DisplayName("Returns empty array when flow has no execution history")
        @SuppressWarnings("unchecked")
        void returnsEmptyArrayWhenNoHistory() {
            when(flowRepository.findFlowTypeForTenant("flow-2", "t1"))
                    .thenReturn(Optional.of(Map.of("id", "flow-2", "flow_type", "SCHEDULED")));
            when(scheduledJobRepository.findRecentRunsForFlow(eq("flow-2"), eq("t1"), anyInt()))
                    .thenReturn(List.of());

            var response = controller.getRuns("flow-2", 50);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            assertThat(data).isEmpty();
        }

        @Test
        @DisplayName("Caps an excessive limit at 200")
        void capsLimitAt200() {
            when(flowRepository.findFlowTypeForTenant("flow-1", "t1"))
                    .thenReturn(Optional.of(Map.of("id", "flow-1", "flow_type", "SCHEDULED")));
            when(scheduledJobRepository.findRecentRunsForFlow(eq("flow-1"), eq("t1"), anyInt()))
                    .thenReturn(List.of());

            controller.getRuns("flow-1", 100_000);

            verify(scheduledJobRepository).findRecentRunsForFlow("flow-1", "t1", 200);
        }

        @Test
        @DisplayName("Returns 404 for an unknown flow")
        void returns404ForUnknownFlow() {
            when(flowRepository.findFlowTypeForTenant("nope", "t1")).thenReturn(Optional.empty());

            var response = controller.getRuns("nope", 50);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(scheduledJobRepository);
        }
    }
}
