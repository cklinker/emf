package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FlowDefinitionValidationHook")
class FlowDefinitionValidationHookTest {

    private FlowDefinitionValidationHook hook;

    @BeforeEach
    void setUp() {
        hook = new FlowDefinitionValidationHook(new ObjectMapper());
    }

    private static Map<String, Object> validDefinition() {
        return new HashMap<>(Map.of(
                "StartAt", "Done",
                "States", Map.of("Done", Map.of("Type", "Succeed"))));
    }

    @Test
    @DisplayName("Targets the flows collection")
    void targetsFlows() {
        assertEquals("flows", hook.getCollectionName());
    }

    @Test
    @DisplayName("Runs before the publisher/scheduler hooks (order 50)")
    void orderIsBeforePublisher() {
        assertTrue(hook.getOrder() < 100,
                "must run before FlowConfigEventPublisher (order 100) so invalid "
                        + "definitions never broadcast to other pods");
    }

    @Test
    @DisplayName("Accepts a valid definition supplied as a Map")
    void acceptsValidMapDefinition() {
        Map<String, Object> record = new HashMap<>();
        record.put("definition", validDefinition());

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess(),
                () -> "expected success, got: " + result.getErrors());
        assertFalse(result.hasFieldUpdates(),
                "validator must not modify the record");
    }

    @Test
    @DisplayName("Accepts a valid definition supplied as a JSON string (jsonb fallback)")
    void acceptsValidStringDefinition() {
        Map<String, Object> record = new HashMap<>();
        record.put("definition", """
                {
                    "StartAt": "Done",
                    "States": { "Done": { "Type": "Succeed" } }
                }
                """);

        assertTrue(hook.beforeCreate(record, "tenant-1").isSuccess());
    }

    @Test
    @DisplayName("Create without a definition field is rejected — flows table requires it")
    void createWithoutDefinitionRejected() {
        Map<String, Object> record = new HashMap<>(Map.of("name", "no-def"));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess());
        assertEquals("definition", result.getErrors().get(0).field());
    }

    @Test
    @DisplayName("Update without a definition field is allowed (partial PUT)")
    void updateWithoutDefinitionAllowed() {
        // e.g. UI toggles `active` without resending the whole flow body
        Map<String, Object> record = new HashMap<>(Map.of("active", false));

        BeforeSaveResult result = hook.beforeUpdate("flow-1", record, Map.of(), "tenant-1");

        assertTrue(result.isSuccess(),
                "partial updates that don't touch definition must pass through");
    }

    @Test
    @DisplayName("Update WITH a definition field still validates it")
    void updateValidatesDefinitionWhenPresent() {
        Map<String, Object> badDefinition = Map.of(
                "States", Map.of("Done", Map.of("Type", "Succeed"))); // no StartAt
        Map<String, Object> record = new HashMap<>();
        record.put("definition", badDefinition);

        BeforeSaveResult result = hook.beforeUpdate("flow-1", record, Map.of(), "tenant-1");

        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Missing StartAt is rejected with field=\"definition\"")
    void missingStartAtRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("definition", Map.of(
                "States", Map.of("Done", Map.of("Type", "Succeed"))));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors());
        BeforeSaveResult.ValidationError err = result.getErrors().get(0);
        assertEquals("definition", err.field(),
                "errors must surface against the 'definition' field so the UI "
                        + "can highlight the JSON editor, not the whole record");
        assertTrue(err.message().contains("StartAt"),
                () -> "message should call out StartAt, got: " + err.message());
    }

    @Test
    @DisplayName("Dangling Next is rejected and the error names the missing target")
    void danglingNextRejected() {
        Map<String, Object> record = new HashMap<>();
        record.put("definition", Map.of(
                "StartAt", "Start",
                "States", Map.of(
                        "Start", Map.of(
                                "Type", "Task",
                                "Resource", "LOG_MESSAGE",
                                "Next", "Ghost"))));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream()
                        .anyMatch(e -> e.message().contains("Ghost")),
                () -> "expected error mentioning Ghost, got: " + result.getErrors());
    }

    @Test
    @DisplayName("Multiple problems all surface in one response — author fixes once")
    void multipleProblemsSurfaceTogether() {
        // Two dangling references: Choice rule Next + Default
        Map<String, Object> definition = Map.of(
                "StartAt", "Pick",
                "States", Map.of(
                        "Pick", Map.of(
                                "Type", "Choice",
                                "Choices", List.of(Map.of(
                                        "Variable", "$.x",
                                        "BooleanEquals", true,
                                        "Next", "Missing1")),
                                "Default", "Missing2")));
        Map<String, Object> record = new HashMap<>();
        record.put("definition", definition);

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess());
        assertEquals(2, result.getErrors().size(),
                () -> "expected both dangling refs, got: " + result.getErrors());
    }
}
