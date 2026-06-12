package io.kelta.worker.listener;

import io.kelta.runtime.flow.FlowDefinitionValidator;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FlowDefinitionValidationHook")
class FlowDefinitionValidationHookTest {

    private FlowDefinitionValidationHook hook;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        hook = new FlowDefinitionValidationHook(new FlowDefinitionValidator(objectMapper), objectMapper);
    }

    @Test
    @DisplayName("targets the 'flows' system collection")
    void targetsFlowsCollection() {
        assertThat(hook.getCollectionName()).isEqualTo("flows");
    }

    @Test
    @DisplayName("runs before event publisher and schedule sync hooks")
    void orderRunsFirst() {
        assertThat(hook.getOrder()).isLessThan(100);
    }

    @Test
    @DisplayName("accepts a valid definition supplied as a Map")
    void acceptsValidMapDefinition() {
        Map<String, Object> record = newRecord(validDefinitionMap());

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("accepts a valid definition supplied as a JSON string")
    void acceptsValidStringDefinition() throws Exception {
        Map<String, Object> record = newRecord(
                objectMapper.writeValueAsString(validDefinitionMap()));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("rejects definition missing StartAt with a field-level error")
    void rejectsMissingStartAt() {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("States", Map.of("Done", Map.of("Type", "Succeed")));
        Map<String, Object> record = newRecord(definition);

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).field()).isEqualTo("definition");
        assertThat(result.getErrors().get(0).message()).contains("StartAt");
    }

    @Test
    @DisplayName("rejects definition with a dangling Next target")
    void rejectsDanglingNext() {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("StartAt", "A");
        definition.put("States", Map.of(
                "A", Map.of("Type", "Task", "Resource", "LOG_MESSAGE", "Next", "Ghost")));
        Map<String, Object> record = newRecord(definition);

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.field()).isEqualTo("definition");
                    assertThat(e.message()).contains("Ghost");
                });
    }

    @Test
    @DisplayName("rejects Map state without an Iterator")
    void rejectsMapWithoutIterator() {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("StartAt", "Loop");
        definition.put("States", Map.of(
                "Loop", Map.of("Type", "Map", "ItemsPath", "$.items", "End", true)));
        Map<String, Object> record = newRecord(definition);

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors().get(0).message()).contains("Iterator");
    }

    @Test
    @DisplayName("collects multiple errors in a single response")
    void collectsMultipleErrors() {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("StartAt", "Ghost");
        definition.put("States", Map.of(
                "A", Map.of("Type", "Task", "Resource", "LOG_MESSAGE", "Next", "Nowhere")));
        Map<String, Object> record = newRecord(definition);

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("update with no definition in payload skips validation")
    void updateSkipsWhenDefinitionAbsent() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "name", "Renamed Flow",
                "active", true));

        BeforeSaveResult result = hook.beforeUpdate("flow-1", record, Map.of(), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("update with malformed definition is rejected")
    void updateRejectsMalformedDefinition() {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("States", Map.of("Done", Map.of("Type", "Succeed")));
        Map<String, Object> record = new HashMap<>();
        record.put("definition", definition);

        BeforeSaveResult result = hook.beforeUpdate("flow-1", record, Map.of(), "tenant-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors().get(0).field()).isEqualTo("definition");
    }

    @Test
    @DisplayName("missing definition field on create is allowed (required check handles it)")
    void missingDefinitionFieldOnCreate() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "name", "Flow w/o def", "flowType", "RECORD_TRIGGERED"));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertThat(result.isSuccess()).isTrue();
    }

    private Map<String, Object> validDefinitionMap() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("StartAt", "Done");
        def.put("States", Map.of("Done", Map.of("Type", "Succeed")));
        return def;
    }

    private Map<String, Object> newRecord(Object definition) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "flow-1");
        record.put("name", "Sample Flow");
        record.put("flowType", "RECORD_TRIGGERED");
        record.put("active", true);
        record.put("definition", definition);
        return record;
    }
}
