package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionHandlerRegistry;
import com.emf.controlplane.service.workflow.ActionResult;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DecisionActionHandlerTest {

    private DecisionActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private FormulaEvaluator formulaEvaluator;
    private ActionHandlerRegistry handlerRegistry;

    @BeforeEach
    void setUp() {
        formulaEvaluator = mock(FormulaEvaluator.class);
        handlerRegistry = mock(ActionHandlerRegistry.class);
        handler = new DecisionActionHandler(objectMapper, formulaEvaluator, handlerRegistry);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("DECISION", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should execute true branch when condition is true")
    void shouldExecuteTrueBranch() {
        when(formulaEvaluator.evaluateBoolean(eq("status == 'High'"), any())).thenReturn(true);

        ActionHandler fieldUpdateHandler = mock(ActionHandler.class);
        when(fieldUpdateHandler.execute(any())).thenReturn(ActionResult.success(Map.of("updatedFields", Map.of("priority", "Urgent"))));
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));

        ActionContext ctx = createContext("""
            {
                "condition": "status == 'High'",
                "trueActions": [
                    {"actionType": "FIELD_UPDATE", "config": {"updates": [{"field": "priority", "value": "Urgent"}]}}
                ],
                "falseActions": [
                    {"actionType": "LOG_MESSAGE", "config": {"message": "Not high"}}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(true, result.outputData().get("conditionResult"));
        assertEquals("true", result.outputData().get("branch"));
        assertEquals(1, result.outputData().get("actionsExecuted"));
        verify(fieldUpdateHandler).execute(any());
    }

    @Test
    @DisplayName("Should execute false branch when condition is false")
    void shouldExecuteFalseBranch() {
        when(formulaEvaluator.evaluateBoolean(eq("status == 'High'"), any())).thenReturn(false);

        ActionHandler logHandler = mock(ActionHandler.class);
        when(logHandler.execute(any())).thenReturn(ActionResult.success(Map.of("message", "Not high")));
        when(handlerRegistry.getHandler("LOG_MESSAGE")).thenReturn(Optional.of(logHandler));

        ActionContext ctx = createContext("""
            {
                "condition": "status == 'High'",
                "trueActions": [
                    {"actionType": "FIELD_UPDATE", "config": {"updates": [{"field": "priority", "value": "Urgent"}]}}
                ],
                "falseActions": [
                    {"actionType": "LOG_MESSAGE", "config": {"message": "Not high"}}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(false, result.outputData().get("conditionResult"));
        assertEquals("false", result.outputData().get("branch"));
        assertEquals(1, result.outputData().get("actionsExecuted"));
        verify(logHandler).execute(any());
    }

    @Test
    @DisplayName("Should handle empty true branch")
    void shouldHandleEmptyTrueBranch() {
        when(formulaEvaluator.evaluateBoolean(anyString(), any())).thenReturn(true);

        ActionContext ctx = createContext("""
            {
                "condition": "status == 'High'",
                "trueActions": [],
                "falseActions": [
                    {"actionType": "LOG_MESSAGE", "config": {"message": "fallback"}}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(0, result.outputData().get("actionsExecuted"));
    }

    @Test
    @DisplayName("Should handle null false branch")
    void shouldHandleNullFalseBranch() {
        when(formulaEvaluator.evaluateBoolean(anyString(), any())).thenReturn(false);

        ActionContext ctx = createContext("""
            {
                "condition": "status == 'High'",
                "trueActions": [
                    {"actionType": "FIELD_UPDATE", "config": {"updates": [{"field": "x", "value": "y"}]}}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(0, result.outputData().get("actionsExecuted"));
    }

    @Test
    @DisplayName("Should fail when condition is missing")
    void shouldFailWhenConditionMissing() {
        ActionContext ctx = createContext("""
            {
                "trueActions": [{"actionType": "LOG_MESSAGE", "config": {"message": "hi"}}]
            }""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("condition is required"));
    }

    @Test
    @DisplayName("Should fail when condition evaluation throws exception")
    void shouldFailWhenConditionEvaluationFails() {
        when(formulaEvaluator.evaluateBoolean(anyString(), any()))
            .thenThrow(new RuntimeException("Invalid expression"));

        ActionContext ctx = createContext("""
            {
                "condition": "invalid!!!",
                "trueActions": [{"actionType": "LOG_MESSAGE", "config": {"message": "hi"}}]
            }""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Condition evaluation error"));
    }

    @Test
    @DisplayName("Should handle nested action failure")
    void shouldHandleNestedActionFailure() {
        when(formulaEvaluator.evaluateBoolean(anyString(), any())).thenReturn(true);

        ActionHandler failingHandler = mock(ActionHandler.class);
        when(failingHandler.execute(any())).thenReturn(ActionResult.failure("Nested action failed"));
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(failingHandler));

        ActionContext ctx = createContext("""
            {
                "condition": "true",
                "trueActions": [
                    {"actionType": "FIELD_UPDATE", "config": {"updates": [{"field": "x", "value": "y"}]}}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful()); // Parent succeeds but reports nested failure
        assertEquals(true, result.outputData().get("nestedFailure"));
    }

    @Test
    @DisplayName("Should skip actions with no handler registered")
    void shouldSkipActionsWithNoHandler() {
        when(formulaEvaluator.evaluateBoolean(anyString(), any())).thenReturn(true);
        when(handlerRegistry.getHandler("UNKNOWN_TYPE")).thenReturn(Optional.empty());

        ActionContext ctx = createContext("""
            {
                "condition": "true",
                "trueActions": [
                    {"actionType": "UNKNOWN_TYPE", "config": {}}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(0, result.outputData().get("actionsExecuted"));
    }

    @Test
    @DisplayName("Should execute multiple actions in branch")
    void shouldExecuteMultipleActionsInBranch() {
        when(formulaEvaluator.evaluateBoolean(anyString(), any())).thenReturn(true);

        ActionHandler handler1 = mock(ActionHandler.class);
        when(handler1.execute(any())).thenReturn(ActionResult.success());
        ActionHandler handler2 = mock(ActionHandler.class);
        when(handler2.execute(any())).thenReturn(ActionResult.success());

        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler1));
        when(handlerRegistry.getHandler("LOG_MESSAGE")).thenReturn(Optional.of(handler2));

        ActionContext ctx = createContext("""
            {
                "condition": "true",
                "trueActions": [
                    {"actionType": "FIELD_UPDATE", "config": {"updates": [{"field": "x", "value": "y"}]}},
                    {"actionType": "LOG_MESSAGE", "config": {"message": "done"}}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals(2, result.outputData().get("actionsExecuted"));
    }

    @Test
    @DisplayName("Validate should reject missing condition")
    void validateShouldRejectMissingCondition() {
        assertThrows(IllegalArgumentException.class, () ->
            handler.validate("{\"trueActions\": []}"));
    }

    @Test
    @DisplayName("Validate should reject empty branches")
    void validateShouldRejectEmptyBranches() {
        assertThrows(IllegalArgumentException.class, () ->
            handler.validate("{\"condition\": \"true\", \"trueActions\": [], \"falseActions\": []}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate(
            "{\"condition\": \"status == 'High'\", \"trueActions\": [{\"actionType\": \"LOG_MESSAGE\", \"config\": {}}]}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "status", "High"))
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
