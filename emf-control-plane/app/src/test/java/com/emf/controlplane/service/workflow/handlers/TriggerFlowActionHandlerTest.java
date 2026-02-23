package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.emf.controlplane.service.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TriggerFlowActionHandlerTest {

    private TriggerFlowActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowRuleRepository ruleRepository;
    private WorkflowEngine workflowEngine;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(WorkflowRuleRepository.class);
        workflowEngine = mock(WorkflowEngine.class);
        handler = new TriggerFlowActionHandler(objectMapper, ruleRepository, workflowEngine);
        TriggerFlowActionHandler.resetDepth();
    }

    @AfterEach
    void tearDown() {
        TriggerFlowActionHandler.resetDepth();
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("TRIGGER_FLOW", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should invoke target workflow rule")
    void shouldInvokeTargetRule() {
        WorkflowRule targetRule = createMockRule("target-rule", "Target Rule", true);
        when(ruleRepository.findById("target-rule")).thenReturn(Optional.of(targetRule));

        ActionContext ctx = createContext("{\"workflowRuleId\": \"target-rule\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("target-rule", result.outputData().get("targetRuleId"));
        assertEquals("Target Rule", result.outputData().get("targetRuleName"));
        assertEquals("EXECUTED", result.outputData().get("status"));
        verify(workflowEngine).evaluateRule(eq(targetRule), any());
    }

    @Test
    @DisplayName("Should skip inactive target rule")
    void shouldSkipInactiveRule() {
        WorkflowRule targetRule = createMockRule("target-rule", "Inactive Rule", false);
        when(ruleRepository.findById("target-rule")).thenReturn(Optional.of(targetRule));

        ActionContext ctx = createContext("{\"workflowRuleId\": \"target-rule\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("SKIPPED", result.outputData().get("status"));
        verify(workflowEngine, never()).evaluateRule(any(), any());
    }

    @Test
    @DisplayName("Should fail when target rule not found")
    void shouldFailWhenTargetRuleNotFound() {
        when(ruleRepository.findById("missing-rule")).thenReturn(Optional.empty());

        ActionContext ctx = createContext("{\"workflowRuleId\": \"missing-rule\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Target workflow rule not found"));
    }

    @Test
    @DisplayName("Should fail when workflowRuleId missing")
    void shouldFailWhenRuleIdMissing() {
        ActionContext ctx = createContext("{}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Target workflow rule ID is required"));
    }

    @Test
    @DisplayName("Should prevent self-referencing loops")
    void shouldPreventSelfReferencing() {
        ActionContext ctx = createContext("{\"workflowRuleId\": \"rule-1\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Cannot trigger a workflow rule from itself"));
    }

    @Test
    @DisplayName("Should track depth counter")
    void shouldTrackDepthCounter() {
        assertEquals(0, TriggerFlowActionHandler.getCurrentDepth());

        WorkflowRule targetRule = createMockRule("target-rule", "Target Rule", true);
        when(ruleRepository.findById("target-rule")).thenReturn(Optional.of(targetRule));

        ActionContext ctx = createContext("{\"workflowRuleId\": \"target-rule\"}");
        handler.execute(ctx);

        // After execution, depth should be back to 0
        assertEquals(0, TriggerFlowActionHandler.getCurrentDepth());
    }

    @Test
    @DisplayName("Validate should reject missing workflowRuleId")
    void validateShouldRejectMissingRuleId() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate("{\"workflowRuleId\": \"some-rule-id\"}"));
    }

    private WorkflowRule createMockRule(String id, String name, boolean active) {
        WorkflowRule rule = mock(WorkflowRule.class);
        when(rule.getId()).thenReturn(id);
        when(rule.getName()).thenReturn(name);
        when(rule.isActive()).thenReturn(active);
        when(rule.getTenantId()).thenReturn("tenant-1");

        Collection collection = mock(Collection.class);
        when(collection.getId()).thenReturn("col-1");
        when(collection.getName()).thenReturn("orders");
        when(rule.getCollection()).thenReturn(collection);

        return rule;
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "status", "Pending"))
            .previousData(Map.of())
            .changedFields(List.of())
            .userId("user-1")
            .actionConfigJson(configJson)
            .workflowRuleId("rule-1")
            .executionLogId("exec-1")
            .resolvedData(Map.of())
            .build();
    }
}
