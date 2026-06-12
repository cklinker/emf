package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FlowScheduleSyncHook")
class FlowScheduleSyncHookTest {

    private ScheduledJobRepository scheduledJobRepository;
    private FlowScheduleSyncHook hook;

    @BeforeEach
    void setUp() {
        scheduledJobRepository = mock(ScheduledJobRepository.class);
        hook = new FlowScheduleSyncHook(scheduledJobRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("targets the flows system collection")
    void targetsFlows() {
        assertThat(hook.getCollectionName()).isEqualTo("flows");
    }

    @Nested
    @DisplayName("validateCron (beforeCreate / beforeUpdate)")
    class ValidateCron {

        @Test
        @DisplayName("accepts a standard 5-field Unix cron — normalization handles seconds")
        void acceptsFiveFieldCron() {
            Map<String, Object> record = scheduledFlow("0 */4 * * *");

            BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("accepts a 6-field Spring cron")
        void acceptsSixFieldCron() {
            Map<String, Object> record = scheduledFlow("0 0 */4 * * *");

            BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("rejects garbage cron loudly with an actionable message")
        void rejectsGarbageCronLoudly() {
            Map<String, Object> record = scheduledFlow("not a cron at all");

            BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            BeforeSaveResult.ValidationError error = result.getErrors().get(0);
            assertThat(error.field()).isEqualTo("triggerConfig.cron");
            // Message must echo the offending value and name the expected format,
            // so users can fix it without grepping logs.
            assertThat(error.message())
                    .contains("not a cron at all")
                    .contains("Spring expression");
        }

        @Test
        @DisplayName("rejects on update too — not just create")
        void rejectsOnUpdate() {
            Map<String, Object> record = scheduledFlow("totally bogus");

            BeforeSaveResult result = hook.beforeUpdate("flow-1", record, new HashMap<>(record), "tenant-1");

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("ignores non-SCHEDULED flows — cron field, if any, is unused")
        void ignoresNonScheduledFlows() {
            Map<String, Object> record = new HashMap<>();
            record.put("id", "flow-1");
            record.put("flowType", "RECORD_TRIGGERED");
            record.put("triggerConfig", Map.of("cron", "garbage"));

            BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("allows SCHEDULED flows without a cron — handled downstream as a no-op sync")
        void allowsMissingCron() {
            Map<String, Object> record = new HashMap<>();
            record.put("id", "flow-1");
            record.put("flowType", "SCHEDULED");
            record.put("triggerConfig", Map.of());

            BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("validates a triggerConfig that arrived as a JSON string (e.g. jsonb passthrough)")
        void validatesJsonStringTriggerConfig() {
            Map<String, Object> record = new HashMap<>();
            record.put("id", "flow-1");
            record.put("flowType", "SCHEDULED");
            record.put("triggerConfig", "{\"cron\":\"definitely bad\"}");

            BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrors().get(0).message()).contains("definitely bad");
        }
    }

    @Nested
    @DisplayName("afterCreate / afterUpdate — scheduled_job sync")
    class ScheduledJobSync {

        @Test
        @DisplayName("inserts scheduled_job with the normalized 6-field cron for a 5-field input")
        void insertsWithNormalizedCron() {
            Map<String, Object> record = scheduledFlow("0 */4 * * *");
            record.put("id", "flow-1");
            record.put("name", "Tubi 4h ingest");
            record.put("createdBy", "user-1");

            when(scheduledJobRepository.findByFlowId("flow-1", "tenant-1")).thenReturn(Optional.empty());

            hook.afterCreate(record, "tenant-1");

            // The row written to scheduled_job MUST be the 6-field form, otherwise the
            // executor's own CronExpression.parse would later throw and the job would
            // never fire — that's exactly the bug this task fixes.
            verify(scheduledJobRepository).insertForFlow(
                    eq("flow-1"),
                    eq("tenant-1"),
                    eq("Tubi 4h ingest"),
                    eq("0 0 */4 * * *"),
                    any(),
                    eq(true),
                    any(Instant.class),
                    eq("user-1")
            );
        }

        @Test
        @DisplayName("updates an existing scheduled_job using the normalized cron")
        void updatesWithNormalizedCron() {
            Map<String, Object> record = scheduledFlow("0 */4 * * *");
            record.put("id", "flow-1");
            record.put("name", "Tubi 4h ingest");

            Map<String, Object> existingJob = new LinkedHashMap<>();
            existingJob.put("id", "job-1");
            when(scheduledJobRepository.findByFlowId("flow-1", "tenant-1"))
                    .thenReturn(Optional.of(existingJob));

            hook.afterCreate(record, "tenant-1");

            verify(scheduledJobRepository).updateForFlow(
                    eq("job-1"),
                    eq("Tubi 4h ingest"),
                    eq("0 0 */4 * * *"),
                    any(),
                    eq(true),
                    any(Instant.class)
            );
        }

        @Test
        @DisplayName("does not silently skip when sync encounters an unparseable cron — logs and skips, but beforeCreate is the guard")
        void skipsRatherThanThrowsOnLegacyInvalidCron() {
            // Simulate legacy/direct-DB-write data: beforeCreate never ran but afterCreate
            // is invoked. The old behavior was silent skip; new behavior is still skip
            // (we can't surface a 400 in an after-hook), but the user-facing path is
            // protected by beforeCreate's validation.
            Map<String, Object> record = scheduledFlow("totally broken");
            record.put("id", "flow-1");

            hook.afterCreate(record, "tenant-1");

            verify(scheduledJobRepository, never())
                    .insertForFlow(anyString(), anyString(), anyString(), anyString(),
                            anyString(), anyBoolean(), any(), anyString());
        }
    }

    private static Map<String, Object> scheduledFlow(String cron) {
        Map<String, Object> record = new HashMap<>();
        record.put("flowType", "SCHEDULED");
        Map<String, Object> triggerConfig = new HashMap<>();
        triggerConfig.put("cron", cron);
        triggerConfig.put("timezone", "UTC");
        record.put("triggerConfig", triggerConfig);
        return record;
    }
}
