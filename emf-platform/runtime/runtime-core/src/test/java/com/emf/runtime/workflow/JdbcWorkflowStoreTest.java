package com.emf.runtime.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("JdbcWorkflowStore")
class JdbcWorkflowStoreTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private JdbcWorkflowStore store;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        store = new JdbcWorkflowStore(jdbcTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should reject null JdbcTemplate")
        void shouldRejectNullJdbcTemplate() {
            assertThrows(NullPointerException.class,
                () -> new JdbcWorkflowStore(null, objectMapper));
        }

        @Test
        @DisplayName("Should reject null ObjectMapper")
        void shouldRejectNullObjectMapper() {
            assertThrows(NullPointerException.class,
                () -> new JdbcWorkflowStore(jdbcTemplate, null));
        }
    }

    @Nested
    @DisplayName("findActiveRules")
    class FindActiveRulesTests {

        @Test
        @DisplayName("Should query with correct SQL containing tenant, collection, and trigger type filters")
        void shouldQueryWithCorrectParameters() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of());

            store.findActiveRules("tenant-1", "orders", "ON_CREATE");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class),
                eq("tenant-1"), eq("orders"), eq("ON_CREATE"));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("wr.tenant_id = ?"));
            assertTrue(sql.contains("c.name = ?"));
            assertTrue(sql.contains("wr.trigger_type = ?"));
            assertTrue(sql.contains("wr.active = true"));
            assertTrue(sql.contains("ORDER BY wr.execution_order ASC"));
        }

        @Test
        @DisplayName("Should return empty list when no rules match")
        void shouldReturnEmptyListWhenNoRulesMatch() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of());

            List<WorkflowRuleData> result = store.findActiveRules("t1", "orders", "ON_CREATE");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should load actions for each rule")
        @SuppressWarnings("unchecked")
        void shouldLoadActionsForEachRule() {
            // Return two rules from the main query (mutable list)
            WorkflowRuleData rule1 = createRule("rule-1", "tenant-1", "col-1", "orders");
            WorkflowRuleData rule2 = createRule("rule-2", "tenant-1", "col-1", "orders");
            List<WorkflowRuleData> mutableRules = new ArrayList<>(List.of(rule1, rule2));

            // Main query: (sql, rowMapper, tenantId, collectionName, triggerType)
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(mutableRules);

            // Action queries: (sql, rowMapper, ruleId) — only 1 trailing arg
            WorkflowActionData action1 = WorkflowActionData.of("a1", "FIELD_UPDATE", 1, "{}", true);
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of(action1));
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("rule-2")))
                .thenReturn(List.of());

            List<WorkflowRuleData> result = store.findActiveRules("tenant-1", "orders", "ON_CREATE");

            assertEquals(2, result.size());
            assertEquals(1, result.get(0).actions().size());
            assertEquals("FIELD_UPDATE", result.get(0).actions().get(0).actionType());
            assertEquals(0, result.get(1).actions().size());
        }
    }

    @Nested
    @DisplayName("findRuleById")
    class FindRuleByIdTests {

        @Test
        @DisplayName("Should return rule when found by ID")
        @SuppressWarnings("unchecked")
        void shouldReturnRuleWhenFound() {
            WorkflowRuleData rule = createRule("rule-1", "tenant-1", "col-1", "orders");

            // Main query: (sql, rowMapper, ruleId)
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of(rule));

            Optional<WorkflowRuleData> result = store.findRuleById("rule-1");

            assertTrue(result.isPresent());
            assertEquals("rule-1", result.get().id());
        }

        @Test
        @DisplayName("Should return empty when rule not found")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyWhenNotFound() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("nonexistent")))
                .thenReturn(List.of());

            Optional<WorkflowRuleData> result = store.findRuleById("nonexistent");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should load actions for the found rule")
        @SuppressWarnings("unchecked")
        void shouldLoadActionsForFoundRule() {
            WorkflowRuleData rule = createRule("rule-1", "tenant-1", "col-1", "orders");

            // First call (main query): returns the rule
            // Second call (action query): returns actions
            WorkflowActionData action = WorkflowActionData.of("a1", "FIELD_UPDATE", 1, "{}", true);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of(rule))
                .thenReturn(List.of(action));

            Optional<WorkflowRuleData> result = store.findRuleById("rule-1");

            assertTrue(result.isPresent());
            assertEquals(1, result.get().actions().size());
            assertEquals("FIELD_UPDATE", result.get().actions().get(0).actionType());
        }

        @Test
        @DisplayName("Should query with correct SQL for active rules by ID")
        void shouldQueryWithCorrectSql() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of());

            store.findRuleById("rule-1");

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq("rule-1"));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("wr.id = ?"));
            assertTrue(sql.contains("wr.active = true"));
        }
    }

    @Nested
    @DisplayName("findScheduledRules")
    class FindScheduledRulesTests {

        @Test
        @DisplayName("Should query for scheduled rules across all tenants")
        void shouldQueryForScheduledRules() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of());

            store.findScheduledRules();

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("trigger_type = 'SCHEDULED'"));
            assertTrue(sql.contains("wr.active = true"));
        }

        @Test
        @DisplayName("Should load actions for scheduled rules")
        @SuppressWarnings("unchecked")
        void shouldLoadActionsForScheduledRules() {
            WorkflowRuleData rule = createRule("rule-1", "t1", "c1", "tasks");
            List<WorkflowRuleData> mutableRules = new ArrayList<>(List.of(rule));
            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mutableRules);

            WorkflowActionData action = WorkflowActionData.of("a1", "EMAIL_ALERT", 1, "{}", true);
            when(jdbcTemplate.query(
                argThat((String sql) -> sql.contains("workflow_action")),
                any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of(action));

            List<WorkflowRuleData> result = store.findScheduledRules();

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).actions().size());
        }
    }

    @Nested
    @DisplayName("createExecutionLog")
    class CreateExecutionLogTests {

        @Test
        @DisplayName("Should insert execution log and return a UUID")
        void shouldInsertExecutionLogAndReturnId() {
            when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);

            String id = store.createExecutionLog("tenant-1", "rule-1", "record-1", "ON_CREATE");

            assertNotNull(id);
            assertFalse(id.isBlank());
            // UUID format: 8-4-4-4-12
            assertEquals(36, id.length());

            // Verify update was called with the SQL and expected args
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

            assertTrue(sqlCaptor.getValue().contains("INSERT INTO workflow_execution_log"));

            Object[] args = argsCaptor.getValue();
            assertEquals(id, args[0]); // id
            assertEquals("tenant-1", args[1]); // tenant_id
            assertEquals("rule-1", args[2]); // workflow_rule_id
            assertEquals("record-1", args[3]); // record_id
            assertEquals("ON_CREATE", args[4]); // trigger_type
            assertInstanceOf(Timestamp.class, args[5]); // executed_at
            assertInstanceOf(Timestamp.class, args[6]); // created_at
            assertInstanceOf(Timestamp.class, args[7]); // updated_at
        }

        @Test
        @DisplayName("Should generate unique IDs")
        void shouldGenerateUniqueIds() {
            when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);

            String id1 = store.createExecutionLog("t1", "r1", "rec1", "ON_CREATE");
            String id2 = store.createExecutionLog("t1", "r1", "rec1", "ON_CREATE");

            assertNotEquals(id1, id2);
        }
    }

    @Nested
    @DisplayName("updateExecutionLog")
    class UpdateExecutionLogTests {

        @Test
        @DisplayName("Should update execution log with all fields")
        void shouldUpdateExecutionLog() {
            when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);

            store.updateExecutionLog("log-1", "SUCCESS", 3, null, 150);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("UPDATE workflow_execution_log"));
            assertTrue(sql.contains("status = ?"));
            assertTrue(sql.contains("actions_executed = ?"));

            Object[] args = argsCaptor.getValue();
            assertEquals("SUCCESS", args[0]);
            assertEquals(3, args[1]);
            assertNull(args[2]); // errorMessage
            assertEquals(150, args[3]); // durationMs
            assertInstanceOf(Timestamp.class, args[4]); // updated_at
            assertEquals("log-1", args[5]); // WHERE id = ?
        }

        @Test
        @DisplayName("Should update with error message on failure")
        void shouldUpdateWithErrorMessage() {
            when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);

            store.updateExecutionLog("log-1", "FAILURE", 1, "Action failed", 50);

            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).update(anyString(), argsCaptor.capture());

            Object[] args = argsCaptor.getValue();
            assertEquals("FAILURE", args[0]);
            assertEquals(1, args[1]);
            assertEquals("Action failed", args[2]);
        }
    }

    @Nested
    @DisplayName("createActionLog")
    class CreateActionLogTests {

        @Test
        @DisplayName("Should insert action log with all fields")
        void shouldInsertActionLog() {
            when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);

            store.createActionLog("exec-1", "action-1", "FIELD_UPDATE",
                "SUCCESS", null, "{\"input\":1}", "{\"output\":2}", 25, 1);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

            assertTrue(sqlCaptor.getValue().contains("INSERT INTO workflow_action_log"));

            Object[] args = argsCaptor.getValue();
            assertNotNull(args[0]); // generated id
            assertEquals("exec-1", args[1]); // execution_log_id
            assertEquals("action-1", args[2]); // action_id
            assertEquals("FIELD_UPDATE", args[3]); // action_type
            assertEquals("SUCCESS", args[4]); // status
            assertNull(args[5]); // error_message
            assertEquals("{\"input\":1}", args[6]); // input_snapshot
            assertEquals("{\"output\":2}", args[7]); // output_snapshot
            assertEquals(25, args[8]); // duration_ms
            assertEquals(1, args[9]); // attempt_number
        }
    }

    @Nested
    @DisplayName("updateLastScheduledRun")
    class UpdateLastScheduledRunTests {

        @Test
        @DisplayName("Should update last scheduled run timestamp")
        void shouldUpdateLastScheduledRun() {
            Instant now = Instant.now();
            when(jdbcTemplate.update(anyString(), (Object[]) any())).thenReturn(1);

            store.updateLastScheduledRun("rule-1", now);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

            assertTrue(sqlCaptor.getValue().contains("UPDATE workflow_rule"));
            assertTrue(sqlCaptor.getValue().contains("last_scheduled_run = ?"));

            Object[] args = argsCaptor.getValue();
            assertEquals(Timestamp.from(now), args[0]); // last_scheduled_run
            assertInstanceOf(Timestamp.class, args[1]); // updated_at
            assertEquals("rule-1", args[2]); // WHERE id = ?
        }
    }

    @Nested
    @DisplayName("claimScheduledRule")
    class ClaimScheduledRuleTests {

        @Test
        @DisplayName("Should claim rule with null lastScheduledRun (never run before)")
        void shouldClaimRuleWithNullLastRun() {
            Instant now = Instant.now();
            // 3 varargs: Timestamp, Timestamp, String
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

            boolean claimed = store.claimScheduledRule("rule-1", null, now);

            assertTrue(claimed);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), any(), any(), any());

            assertTrue(sqlCaptor.getValue().contains("last_scheduled_run IS NULL"));
        }

        @Test
        @DisplayName("Should claim rule with matching lastScheduledRun")
        void shouldClaimRuleWithMatchingLastRun() {
            Instant lastRun = Instant.now().minusSeconds(3600);
            Instant now = Instant.now();
            // 4 varargs: Timestamp, Timestamp, String, Timestamp
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

            boolean claimed = store.claimScheduledRule("rule-1", lastRun, now);

            assertTrue(claimed);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), any(), any(), any(), any());

            assertTrue(sqlCaptor.getValue().contains("last_scheduled_run = ?"));
        }

        @Test
        @DisplayName("Should return false when claim fails (already claimed)")
        void shouldReturnFalseWhenClaimFails() {
            // 3 varargs for null lastRun case
            when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(0);

            boolean claimed = store.claimScheduledRule("rule-1", null, Instant.now());

            assertFalse(claimed);
        }
    }

    @Nested
    @DisplayName("Row mapping")
    class RowMappingTests {

        @Test
        @DisplayName("Should correctly map rule from ResultSet via findActiveRules")
        @SuppressWarnings("unchecked")
        void shouldMapRuleFromResultSet() throws SQLException {
            ResultSet rs = createMockResultSet(
                "rule-1", "tenant-1", "col-1", "orders",
                "Auto Close", "Closes orders automatically",
                true, "ON_UPDATE", "status == 'Done'",
                false, 1, "STOP_ON_ERROR",
                "[\"status\"]", null, null, null, "SEQUENTIAL");

            // Capture the RowMapper to test it directly
            ArgumentCaptor<RowMapper<WorkflowRuleData>> mapperCaptor =
                ArgumentCaptor.forClass(RowMapper.class);
            when(jdbcTemplate.query(anyString(), mapperCaptor.capture(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<WorkflowRuleData> mapper = mapperCaptor.getValue();
                    List<WorkflowRuleData> result = new ArrayList<>();
                    result.add(mapper.mapRow(rs, 0));
                    return result;
                });

            // No actions
            when(jdbcTemplate.query(
                argThat((String sql) -> sql.contains("workflow_action")),
                any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of());

            List<WorkflowRuleData> result = store.findActiveRules("tenant-1", "orders", "ON_UPDATE");

            assertEquals(1, result.size());
            WorkflowRuleData rule = result.get(0);
            assertEquals("rule-1", rule.id());
            assertEquals("tenant-1", rule.tenantId());
            assertEquals("col-1", rule.collectionId());
            assertEquals("orders", rule.collectionName());
            assertEquals("Auto Close", rule.name());
            assertEquals("Closes orders automatically", rule.description());
            assertTrue(rule.active());
            assertEquals("ON_UPDATE", rule.triggerType());
            assertEquals("status == 'Done'", rule.filterFormula());
            assertFalse(rule.reEvaluateOnUpdate());
            assertEquals(1, rule.executionOrder());
            assertEquals("STOP_ON_ERROR", rule.errorHandling());
            assertEquals(List.of("status"), rule.triggerFields());
            assertNull(rule.cronExpression());
            assertNull(rule.timezone());
            assertNull(rule.lastScheduledRun());
            assertEquals("SEQUENTIAL", rule.executionMode());
        }

        @Test
        @DisplayName("Should handle null trigger_fields JSON")
        @SuppressWarnings("unchecked")
        void shouldHandleNullTriggerFields() throws SQLException {
            ResultSet rs = createMockResultSet(
                "rule-1", "t1", "c1", "orders",
                "Test Rule", null,
                true, "ON_CREATE", null,
                false, 0, "CONTINUE_ON_ERROR",
                null, null, null, null, "SEQUENTIAL");

            ArgumentCaptor<RowMapper<WorkflowRuleData>> mapperCaptor =
                ArgumentCaptor.forClass(RowMapper.class);
            when(jdbcTemplate.query(anyString(), mapperCaptor.capture(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<WorkflowRuleData> mapper = mapperCaptor.getValue();
                    List<WorkflowRuleData> result = new ArrayList<>();
                    result.add(mapper.mapRow(rs, 0));
                    return result;
                });
            when(jdbcTemplate.query(
                argThat((String sql) -> sql.contains("workflow_action")),
                any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of());

            List<WorkflowRuleData> result = store.findActiveRules("t1", "orders", "ON_CREATE");

            assertNull(result.get(0).triggerFields());
        }

        @Test
        @DisplayName("Should handle invalid trigger_fields JSON gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandleInvalidTriggerFieldsJson() throws SQLException {
            ResultSet rs = createMockResultSet(
                "rule-1", "t1", "c1", "orders",
                "Test Rule", null,
                true, "ON_CREATE", null,
                false, 0, "CONTINUE_ON_ERROR",
                "not-valid-json", null, null, null, "SEQUENTIAL");

            ArgumentCaptor<RowMapper<WorkflowRuleData>> mapperCaptor =
                ArgumentCaptor.forClass(RowMapper.class);
            when(jdbcTemplate.query(anyString(), mapperCaptor.capture(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<WorkflowRuleData> mapper = mapperCaptor.getValue();
                    List<WorkflowRuleData> result = new ArrayList<>();
                    result.add(mapper.mapRow(rs, 0));
                    return result;
                });
            when(jdbcTemplate.query(
                argThat((String sql) -> sql.contains("workflow_action")),
                any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of());

            List<WorkflowRuleData> result = store.findActiveRules("t1", "orders", "ON_CREATE");

            // Should return null for invalid JSON (logged as warning)
            assertNull(result.get(0).triggerFields());
        }

        @Test
        @DisplayName("Should parse last_scheduled_run timestamp")
        @SuppressWarnings("unchecked")
        void shouldParseLastScheduledRun() throws SQLException {
            Instant scheduled = Instant.parse("2025-01-15T10:30:00Z");
            ResultSet rs = createMockResultSet(
                "rule-1", "t1", "c1", "orders",
                "Test Rule", null,
                true, "ON_CREATE", null,
                false, 0, "CONTINUE_ON_ERROR",
                null, null, null, Timestamp.from(scheduled), "SEQUENTIAL");

            ArgumentCaptor<RowMapper<WorkflowRuleData>> mapperCaptor =
                ArgumentCaptor.forClass(RowMapper.class);
            when(jdbcTemplate.query(anyString(), mapperCaptor.capture(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<WorkflowRuleData> mapper = mapperCaptor.getValue();
                    List<WorkflowRuleData> result = new ArrayList<>();
                    result.add(mapper.mapRow(rs, 0));
                    return result;
                });
            when(jdbcTemplate.query(
                argThat((String sql) -> sql.contains("workflow_action")),
                any(RowMapper.class), eq("rule-1")))
                .thenReturn(List.of());

            List<WorkflowRuleData> result = store.findActiveRules("t1", "orders", "ON_CREATE");

            assertEquals(scheduled, result.get(0).lastScheduledRun());
        }

        private ResultSet createMockResultSet(
                String id, String tenantId, String collectionId, String collectionName,
                String name, String description, boolean active, String triggerType,
                String filterFormula, boolean reEvaluateOnUpdate, int executionOrder,
                String errorHandling, String triggerFields, String cronExpression,
                String timezone, Timestamp lastScheduledRun, String executionMode
        ) throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("id")).thenReturn(id);
            when(rs.getString("tenant_id")).thenReturn(tenantId);
            when(rs.getString("collection_id")).thenReturn(collectionId);
            when(rs.getString("collection_name")).thenReturn(collectionName);
            when(rs.getString("name")).thenReturn(name);
            when(rs.getString("description")).thenReturn(description);
            when(rs.getBoolean("active")).thenReturn(active);
            when(rs.getString("trigger_type")).thenReturn(triggerType);
            when(rs.getString("filter_formula")).thenReturn(filterFormula);
            when(rs.getBoolean("re_evaluate_on_update")).thenReturn(reEvaluateOnUpdate);
            when(rs.getInt("execution_order")).thenReturn(executionOrder);
            when(rs.getString("error_handling")).thenReturn(errorHandling);
            when(rs.getString("trigger_fields")).thenReturn(triggerFields);
            when(rs.getString("cron_expression")).thenReturn(cronExpression);
            when(rs.getString("timezone")).thenReturn(timezone);
            when(rs.getTimestamp("last_scheduled_run")).thenReturn(lastScheduledRun);
            when(rs.getString("execution_mode")).thenReturn(executionMode);
            return rs;
        }
    }

    // ---- Helper methods ----

    private WorkflowRuleData createRule(String id, String tenantId, String collectionId,
                                         String collectionName) {
        return new WorkflowRuleData(
            id, tenantId, collectionId, collectionName,
            "Test Rule", null, true, "ON_CREATE",
            null, false, 0, "CONTINUE_ON_ERROR",
            null, null, null, null, "SEQUENTIAL",
            List.of());
    }
}
