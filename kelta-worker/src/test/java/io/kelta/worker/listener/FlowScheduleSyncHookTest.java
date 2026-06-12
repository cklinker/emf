package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.ScheduledJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FlowScheduleSyncHook")
class FlowScheduleSyncHookTest {

    private ScheduledJobRepository repository;
    private FlowScheduleSyncHook hook;

    @BeforeEach
    void setUp() {
        repository = mock(ScheduledJobRepository.class);
        hook = new FlowScheduleSyncHook(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("Targets the flows collection")
    void targetsFlows() {
        assertEquals("flows", hook.getCollectionName());
    }

    @Test
    @DisplayName("Non-SCHEDULED flows are passed through untouched")
    void nonScheduledFlowPassesThrough() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "RECORD_TRIGGERED",
                "triggerConfig", Map.of("cron", "not-relevant")));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess());
        assertFalse(result.hasFieldUpdates());
    }

    @Test
    @DisplayName("Accepts a 5-field cron and rewrites triggerConfig.cron to 6-field")
    void acceptsFiveFieldCronAndRewrites() {
        Map<String, Object> triggerConfig = new LinkedHashMap<>();
        triggerConfig.put("cron", "0 */4 * * *");
        triggerConfig.put("timezone", "UTC");
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "triggerConfig", triggerConfig));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess(), "5-field cron must be accepted");
        assertTrue(result.hasFieldUpdates(),
                "hook should rewrite triggerConfig to the normalized form");
        @SuppressWarnings("unchecked")
        Map<String, Object> updated =
                (Map<String, Object>) result.getFieldUpdates().get("triggerConfig");
        assertEquals("0 0 */4 * * *", updated.get("cron"));
        assertEquals("UTC", updated.get("timezone"),
                "non-cron fields in triggerConfig must be preserved");
    }

    @Test
    @DisplayName("Passes a valid 6-field cron through without rewriting")
    void passesSixFieldCronThrough() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "triggerConfig", Map.of("cron", "0 0 */4 * * *")));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess());
        assertFalse(result.hasFieldUpdates(),
                "6-field cron should not produce a rewrite");
    }

    @Test
    @DisplayName("Rejects garbage cron with an actionable error (400 at write path)")
    void rejectsGarbageCronLoudly() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "triggerConfig", Map.of("cron", "every-tuesday")));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess(),
                "garbage cron must fail validation — silent skip is the bug");
        assertTrue(result.hasErrors());
        assertEquals("triggerConfig.cron", result.getErrors().get(0).field());
        assertTrue(result.getErrors().get(0).message().contains("'every-tuesday'"),
                "error message should quote the offending value");
    }

    @Test
    @DisplayName("Rejects an unparseable 6-field cron loudly")
    void rejectsInvalidSixFieldCronLoudly() {
        // 99 is out of range for seconds.
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "triggerConfig", Map.of("cron", "99 * * * * *")));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors());
        assertEquals("triggerConfig.cron", result.getErrors().get(0).field());
    }

    @Test
    @DisplayName("Tolerates a SCHEDULED flow with no triggerConfig (partial update)")
    void tolerantOfMissingTriggerConfig() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "active", false));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess(),
                "partial update without triggerConfig must not block the save");
    }

    @Test
    @DisplayName("Tolerates triggerConfig with no cron field")
    void tolerantOfMissingCron() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "triggerConfig", Map.of("timezone", "UTC")));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Parses triggerConfig from a JSON string payload")
    void parsesTriggerConfigJsonString() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "triggerConfig", "{\"cron\":\"0 */4 * * *\",\"timezone\":\"UTC\"}"));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess());
        assertTrue(result.hasFieldUpdates());
        @SuppressWarnings("unchecked")
        Map<String, Object> updated =
                (Map<String, Object>) result.getFieldUpdates().get("triggerConfig");
        assertEquals("0 0 */4 * * *", updated.get("cron"));
    }

    @Test
    @DisplayName("beforeUpdate uses the same validation as beforeCreate")
    void beforeUpdateValidates() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "flowType", "SCHEDULED",
                "triggerConfig", Map.of("cron", "broken")));

        BeforeSaveResult result = hook.beforeUpdate("flow-1", record, Map.of(), "tenant-1");

        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("afterCreate persists the scheduled_job with the normalized cron")
    void afterCreatePersistsNormalizedCron() {
        when(repository.findByFlowId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        Map<String, Object> record = new HashMap<>(Map.of(
                "id", "flow-1",
                "name", "Tubi refresh",
                "flowType", "SCHEDULED",
                "active", true,
                "createdBy", "user-1",
                // Simulate the post-beforeCreate state: triggerConfig.cron is the
                // canonical 6-field form because the hook rewrote it.
                "triggerConfig", Map.of("cron", "0 0 */4 * * *", "timezone", "UTC")));

        hook.afterCreate(record, "tenant-1");

        verify(repository).insertForFlow(
                eq("flow-1"), eq("tenant-1"), eq("Tubi refresh"),
                eq("0 0 */4 * * *"), eq("UTC"), eq(true),
                notNull(), eq("user-1"));
    }

    @Test
    @DisplayName("afterCreate normalizes 5-field crons when called directly (defense-in-depth)")
    void afterCreateNormalizesFiveFieldDirectly() {
        // Simulate a caller that bypasses the lifecycle hooks (e.g., seed-data
        // ingestion writing straight to storage) and lands a 5-field cron in the
        // record handed to afterCreate.
        when(repository.findByFlowId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        Map<String, Object> record = new HashMap<>(Map.of(
                "id", "flow-2",
                "name", "Provider refresh",
                "flowType", "SCHEDULED",
                "active", true,
                "createdBy", "user-1",
                "triggerConfig", Map.of("cron", "0 */4 * * *", "timezone", "UTC")));

        hook.afterCreate(record, "tenant-1");

        verify(repository).insertForFlow(
                eq("flow-2"), eq("tenant-1"), eq("Provider refresh"),
                eq("0 0 */4 * * *"), eq("UTC"), eq(true),
                notNull(), eq("user-1"));
    }
}
