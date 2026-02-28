package com.emf.worker.listener;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.flow.FlowEngine;
import com.emf.runtime.flow.FlowTriggerEvaluator;
import com.emf.runtime.flow.InitialStateBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FlowEventListener")
class FlowEventListenerTest {

    private FlowEngine flowEngine;
    private FlowTriggerEvaluator triggerEvaluator;
    private InitialStateBuilder initialStateBuilder;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private FlowEventListener listener;

    @BeforeEach
    void setUp() {
        flowEngine = mock(FlowEngine.class);
        triggerEvaluator = mock(FlowTriggerEvaluator.class);
        initialStateBuilder = mock(InitialStateBuilder.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        listener = new FlowEventListener(flowEngine, triggerEvaluator, initialStateBuilder, jdbcTemplate, objectMapper);
    }

    @Nested
    @DisplayName("trigger_config parsing")
    class TriggerConfigParsing {

        @Test
        @DisplayName("Should parse plain trigger_config format correctly")
        @SuppressWarnings("unchecked")
        void shouldParsePlainTriggerConfig() throws Exception {
            String plainConfig = """
                {"events": ["CREATED", "UPDATED"], "collection": "orders"}
                """;

            stubJdbcWithTriggerConfig(plainConfig);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(true);
            when(initialStateBuilder.buildFromRecordEvent(any(), any(), any())).thenReturn(Map.of());
            when(flowEngine.startExecution(any(), any(), any(), any(), any(), anyBoolean())).thenReturn("exec-1");

            listener.handleRecordChanged(buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED));

            ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
            verify(triggerEvaluator).matchesRecordTrigger(any(), configCaptor.capture());

            Map<String, Object> capturedConfig = configCaptor.getValue();
            assertThat(capturedConfig.get("collection")).isEqualTo("orders");
            assertThat(capturedConfig.get("events")).isEqualTo(List.of("CREATED", "UPDATED"));
            assertThat(capturedConfig).doesNotContainKey("type");
            assertThat(capturedConfig).doesNotContainKey("value");
        }

        @Test
        @DisplayName("Should unwrap jsonb wrapper format to extract inner trigger_config")
        @SuppressWarnings("unchecked")
        void shouldUnwrapJsonbWrapper() throws Exception {
            String wrappedConfig = """
                {"null": false, "type": "jsonb", "value": "{\\"events\\": [\\"CREATED\\", \\"UPDATED\\"], \\"collection\\": \\"orders\\"}"}
                """;

            stubJdbcWithTriggerConfig(wrappedConfig);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(true);
            when(initialStateBuilder.buildFromRecordEvent(any(), any(), any())).thenReturn(Map.of());
            when(flowEngine.startExecution(any(), any(), any(), any(), any(), anyBoolean())).thenReturn("exec-1");

            listener.handleRecordChanged(buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED));

            ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
            verify(triggerEvaluator).matchesRecordTrigger(any(), configCaptor.capture());

            Map<String, Object> capturedConfig = configCaptor.getValue();
            assertThat(capturedConfig.get("collection")).isEqualTo("orders");
            assertThat(capturedConfig.get("events")).isEqualTo(List.of("CREATED", "UPDATED"));
            // After unwrapping, the wrapper keys should NOT be present
            assertThat(capturedConfig).doesNotContainKey("type");
            assertThat(capturedConfig).doesNotContainKey("null");
            assertThat(capturedConfig).doesNotContainKey("value");
        }

        @Test
        @DisplayName("Should handle null trigger_config gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandleNullTriggerConfig() throws Exception {
            stubJdbcWithTriggerConfig(null);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(false);

            listener.handleRecordChanged(buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED));

            ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
            verify(triggerEvaluator).matchesRecordTrigger(any(), configCaptor.capture());

            Map<String, Object> capturedConfig = configCaptor.getValue();
            assertThat(capturedConfig).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty trigger_config gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyTriggerConfig() throws Exception {
            stubJdbcWithTriggerConfig("   ");
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(false);

            listener.handleRecordChanged(buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED));

            ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
            verify(triggerEvaluator).matchesRecordTrigger(any(), configCaptor.capture());

            Map<String, Object> capturedConfig = configCaptor.getValue();
            assertThat(capturedConfig).isEmpty();
        }

        @Test
        @DisplayName("Should not unwrap when type is not jsonb")
        @SuppressWarnings("unchecked")
        void shouldNotUnwrapWhenTypeIsNotJsonb() throws Exception {
            // A config that happens to have a "type" key but with a different value
            String configWithType = """
                {"type": "text", "value": "something", "collection": "orders"}
                """;

            stubJdbcWithTriggerConfig(configWithType);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(false);

            listener.handleRecordChanged(buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED));

            ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
            verify(triggerEvaluator).matchesRecordTrigger(any(), configCaptor.capture());

            Map<String, Object> capturedConfig = configCaptor.getValue();
            // Should keep the original map since type != "jsonb"
            assertThat(capturedConfig.get("type")).isEqualTo("text");
            assertThat(capturedConfig.get("value")).isEqualTo("something");
            assertThat(capturedConfig.get("collection")).isEqualTo("orders");
        }

        @Test
        @DisplayName("Should not unwrap when type is jsonb but value is missing")
        @SuppressWarnings("unchecked")
        void shouldNotUnwrapWhenValueMissing() throws Exception {
            String configWithTypeOnly = """
                {"type": "jsonb", "collection": "orders"}
                """;

            stubJdbcWithTriggerConfig(configWithTypeOnly);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(false);

            listener.handleRecordChanged(buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED));

            ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
            verify(triggerEvaluator).matchesRecordTrigger(any(), configCaptor.capture());

            Map<String, Object> capturedConfig = configCaptor.getValue();
            assertThat(capturedConfig.get("type")).isEqualTo("jsonb");
            assertThat(capturedConfig.get("collection")).isEqualTo("orders");
        }

        @Test
        @DisplayName("Should handle invalid JSON in wrapper value gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandleInvalidJsonInWrapperValue() throws Exception {
            String wrappedWithBadInner = """
                {"null": false, "type": "jsonb", "value": "not valid json"}
                """;

            stubJdbcWithTriggerConfig(wrappedWithBadInner);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(false);

            // Should not throw; the warn log is emitted and an empty config is used
            listener.handleRecordChanged(buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED));

            // The trigger evaluator should still be called (with empty map from the catch block)
            verify(triggerEvaluator).matchesRecordTrigger(any(), any());
        }
    }

    @Nested
    @DisplayName("cache management")
    class CacheManagement {

        @Test
        @DisplayName("Should cache flow configs and not re-query on second event")
        @SuppressWarnings("unchecked")
        void shouldCacheFlowConfigs() throws Exception {
            String plainConfig = """
                {"events": ["CREATED"], "collection": "orders"}
                """;

            stubJdbcWithTriggerConfig(plainConfig);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(false);

            String event = buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED);
            listener.handleRecordChanged(event);
            listener.handleRecordChanged(event);

            // JDBC should only be called once (cached on second call)
            verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), eq("tenant-1"));
        }

        @Test
        @DisplayName("Should re-query after cache invalidation")
        @SuppressWarnings("unchecked")
        void shouldReQueryAfterCacheInvalidation() throws Exception {
            String plainConfig = """
                {"events": ["CREATED"], "collection": "orders"}
                """;

            stubJdbcWithTriggerConfig(plainConfig);
            when(triggerEvaluator.matchesRecordTrigger(any(), any())).thenReturn(false);

            String event = buildRecordChangeEvent("tenant-1", "orders", ChangeType.CREATED);
            listener.handleRecordChanged(event);

            listener.invalidateCache("tenant-1");

            listener.handleRecordChanged(event);

            // JDBC should be called twice (cache was invalidated between)
            verify(jdbcTemplate, times(2)).query(anyString(), any(RowMapper.class), eq("tenant-1"));
        }
    }

    // --- Helper methods ---

    @SuppressWarnings("unchecked")
    private void stubJdbcWithTriggerConfig(String triggerConfigJson) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenAnswer(invocation -> {
                    RowMapper<FlowEventListener.FlowTriggerConfig> mapper = invocation.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString("id")).thenReturn("flow-1");
                    when(rs.getString("definition")).thenReturn("{}");
                    when(rs.getString("trigger_config")).thenReturn(triggerConfigJson);
                    return List.of(mapper.mapRow(rs, 0));
                });
    }

    private String buildRecordChangeEvent(String tenantId, String collectionName, ChangeType changeType) throws Exception {
        RecordChangeEvent event = new RecordChangeEvent();
        event.setEventId("evt-1");
        event.setTenantId(tenantId);
        event.setCollectionName(collectionName);
        event.setRecordId("rec-1");
        event.setChangeType(changeType);
        event.setData(Map.of("name", "test"));
        event.setChangedFields(List.of());
        event.setUserId("user-1");
        return objectMapper.writeValueAsString(event);
    }
}
