package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.emf.runtime.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TriggerFlowActionHandler")
class TriggerFlowActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        TriggerFlowActionHandler handler = new TriggerFlowActionHandler(objectMapper);
        assertEquals("TRIGGER_FLOW", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should validate config requires workflowRuleId")
    void shouldValidateConfig() {
        TriggerFlowActionHandler handler = new TriggerFlowActionHandler(objectMapper);
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Should accept valid config with workflowRuleId")
    void shouldAcceptValidConfig() {
        TriggerFlowActionHandler handler = new TriggerFlowActionHandler(objectMapper);
        assertDoesNotThrow(() -> handler.validate("{\"workflowRuleId\": \"rule-123\"}"));
    }

    @Nested
    @DisplayName("Without WorkflowEngine")
    class WithoutEngineTests {

        private final TriggerFlowActionHandler handler = new TriggerFlowActionHandler(objectMapper);

        @Test
        @DisplayName("Should return failure when WorkflowEngine is not available")
        void shouldReturnFailureWithoutEngine() {
            String config = """
                {"workflowRuleId": "target-rule-123"}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

            ActionResult result = handler.execute(ctx);
            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("WorkflowEngine"));
        }
    }

    @Nested
    @DisplayName("With WorkflowEngine")
    class WithEngineTests {

        private final WorkflowEngine workflowEngine = mock(WorkflowEngine.class);
        private final TriggerFlowActionHandler handler = new TriggerFlowActionHandler(objectMapper, workflowEngine);

        @Test
        @DisplayName("Should execute target rule via WorkflowEngine")
        void shouldExecuteTargetRule() {
            String config = """
                {"workflowRuleId": "target-rule-123"}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of("status", "Active")).actionConfigJson(config)
                .workflowRuleId("wf1").executionLogId("log1").userId("user-1").build();

            when(workflowEngine.executeRuleById(
                eq("target-rule-123"), eq("r1"), eq("t1"), eq("orders"),
                any(), eq("user-1")))
                .thenReturn("exec-log-1");

            ActionResult result = handler.execute(ctx);

            assertTrue(result.successful());
            assertEquals("target-rule-123", result.outputData().get("targetRuleId"));
            assertEquals("exec-log-1", result.outputData().get("executionLogId"));
        }

        @Test
        @DisplayName("Should return failure when target rule not found")
        void shouldFailWhenTargetRuleNotFound() {
            String config = """
                {"workflowRuleId": "nonexistent-rule"}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson(config)
                .workflowRuleId("wf1").executionLogId("log1").userId("user-1").build();

            when(workflowEngine.executeRuleById(anyString(), anyString(), anyString(),
                anyString(), any(), anyString()))
                .thenReturn(null);

            ActionResult result = handler.execute(ctx);

            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("nonexistent-rule"));
            assertTrue(result.errorMessage().contains("not found"));
        }

        @Test
        @DisplayName("Should fail when workflowRuleId is blank in config")
        void shouldFailWhenRuleIdBlank() {
            String config = """
                {"workflowRuleId": "  "}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson(config)
                .workflowRuleId("wf1").executionLogId("log1").build();

            ActionResult result = handler.execute(ctx);

            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("workflowRuleId"));
        }

        @Test
        @DisplayName("Should handle invalid JSON config gracefully")
        void shouldHandleInvalidJsonConfig() {
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson("not-json")
                .workflowRuleId("wf1").executionLogId("log1").build();

            ActionResult result = handler.execute(ctx);

            assertFalse(result.successful());
        }

        @Test
        @DisplayName("Should pass record data to WorkflowEngine")
        void shouldPassRecordDataToEngine() {
            Map<String, Object> data = Map.of("status", "Approved", "amount", 100);
            String config = """
                {"workflowRuleId": "approval-flow"}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(data).actionConfigJson(config)
                .workflowRuleId("wf1").executionLogId("log1").userId("admin").build();

            when(workflowEngine.executeRuleById(
                eq("approval-flow"), eq("r1"), eq("t1"), eq("orders"),
                eq(data), eq("admin")))
                .thenReturn("exec-log-1");

            ActionResult result = handler.execute(ctx);

            assertTrue(result.successful());
            verify(workflowEngine).executeRuleById(
                "approval-flow", "r1", "t1", "orders", data, "admin");
        }
    }
}
