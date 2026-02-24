package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TriggerFlowActionHandler (Stub)")
class TriggerFlowActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TriggerFlowActionHandler handler = new TriggerFlowActionHandler(objectMapper);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("TRIGGER_FLOW", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should return failure indicating Phase 3 requirement")
    void shouldReturnPhase3Failure() {
        String config = """
            {"workflowRuleId": "target-rule-123"}
            """;
        ActionContext ctx = ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of()).actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

        ActionResult result = handler.execute(ctx);
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Phase 3"));
    }

    @Test
    @DisplayName("Should validate config requires workflowRuleId")
    void shouldValidateConfig() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }
}
