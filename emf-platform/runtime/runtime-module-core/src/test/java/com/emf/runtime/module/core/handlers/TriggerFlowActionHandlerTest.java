package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TriggerFlowActionHandler")
class TriggerFlowActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TriggerFlowActionHandler handler = new TriggerFlowActionHandler(objectMapper);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("TRIGGER_FLOW", handler.getActionTypeKey());
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Should validate config requires flowId")
        void shouldValidateConfigRequiresFlowId() {
            assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
        }

        @Test
        @DisplayName("Should accept valid config with flowId")
        void shouldAcceptValidConfigWithFlowId() {
            assertDoesNotThrow(() -> handler.validate("{\"flowId\": \"flow-123\"}"));
        }

        @Test
        @DisplayName("Should accept legacy config with workflowRuleId for backward compatibility")
        void shouldAcceptLegacyConfig() {
            assertDoesNotThrow(() -> handler.validate("{\"workflowRuleId\": \"rule-123\"}"));
        }
    }

    @Nested
    @DisplayName("Execution")
    class ExecutionTests {

        @Test
        @DisplayName("Should succeed when flowId is provided")
        void shouldSucceedWithFlowId() {
            String config = """
                {"flowId": "target-flow-123"}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

            ActionResult result = handler.execute(ctx);

            assertTrue(result.successful());
            assertEquals("target-flow-123", result.outputData().get("flowId"));
            assertEquals("QUEUED", result.outputData().get("status"));
        }

        @Test
        @DisplayName("Should fail when flowId is missing")
        void shouldFailWhenFlowIdMissing() {
            String config = "{}";
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

            ActionResult result = handler.execute(ctx);

            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("flowId"));
        }

        @Test
        @DisplayName("Should fail when flowId is blank")
        void shouldFailWhenFlowIdBlank() {
            String config = """
                {"flowId": "  "}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

            ActionResult result = handler.execute(ctx);

            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("flowId"));
        }

        @Test
        @DisplayName("Should return failure for legacy workflowRuleId config")
        void shouldReturnFailureForLegacyConfig() {
            String config = """
                {"workflowRuleId": "rule-123"}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of()).actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

            ActionResult result = handler.execute(ctx);

            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("deprecated"));
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
    }
}
