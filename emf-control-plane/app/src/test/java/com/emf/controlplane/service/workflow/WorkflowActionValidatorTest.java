package com.emf.controlplane.service.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowActionValidatorTest {

    private WorkflowActionValidator validator;
    private ActionHandlerRegistry handlerRegistry;

    @BeforeEach
    void setUp() {
        handlerRegistry = mock(ActionHandlerRegistry.class);
        validator = new WorkflowActionValidator(handlerRegistry);
    }

    @Test
    @DisplayName("Should return null for valid action config")
    void shouldReturnNullForValidConfig() {
        ActionHandler handler = mock(ActionHandler.class);
        doNothing().when(handler).validate(anyString());
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

        String error = validator.validateAction("FIELD_UPDATE",
            "{\"updates\": [{\"field\": \"status\", \"value\": \"Done\"}]}");

        assertNull(error);
    }

    @Test
    @DisplayName("Should return error for unknown action type")
    void shouldReturnErrorForUnknownType() {
        when(handlerRegistry.getHandler("UNKNOWN")).thenReturn(Optional.empty());

        String error = validator.validateAction("UNKNOWN", "{}");

        assertNotNull(error);
        assertTrue(error.contains("Unknown action type"));
    }

    @Test
    @DisplayName("Should return error for invalid config")
    void shouldReturnErrorForInvalidConfig() {
        ActionHandler handler = mock(ActionHandler.class);
        doThrow(new IllegalArgumentException("Missing 'updates'"))
            .when(handler).validate(anyString());
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

        String error = validator.validateAction("FIELD_UPDATE", "{}");

        assertNotNull(error);
        assertTrue(error.contains("Missing 'updates'"));
    }

    @Test
    @DisplayName("Should validate all actions and collect errors")
    void shouldValidateAllActions() {
        ActionHandler validHandler = mock(ActionHandler.class);
        doNothing().when(validHandler).validate(anyString());
        when(handlerRegistry.getHandler("LOG_MESSAGE")).thenReturn(Optional.of(validHandler));

        ActionHandler invalidHandler = mock(ActionHandler.class);
        doThrow(new IllegalArgumentException("Missing field"))
            .when(invalidHandler).validate(anyString());
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(invalidHandler));

        List<String> errors = validator.validateActions(List.of(
            new WorkflowActionValidator.ActionDefinition("LOG_MESSAGE", "{\"message\": \"test\"}"),
            new WorkflowActionValidator.ActionDefinition("FIELD_UPDATE", "{}"),
            new WorkflowActionValidator.ActionDefinition("UNKNOWN_TYPE", "{}")
        ));

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("Action 2"));
        assertTrue(errors.get(1).contains("Action 3"));
    }

    @Test
    @DisplayName("Should return empty list for all valid actions")
    void shouldReturnEmptyForAllValid() {
        ActionHandler handler = mock(ActionHandler.class);
        doNothing().when(handler).validate(anyString());
        when(handlerRegistry.getHandler(anyString())).thenReturn(Optional.of(handler));

        List<String> errors = validator.validateActions(List.of(
            new WorkflowActionValidator.ActionDefinition("FIELD_UPDATE", "{\"updates\": []}"),
            new WorkflowActionValidator.ActionDefinition("LOG_MESSAGE", "{\"message\": \"test\"}")
        ));

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for null input")
    void shouldReturnEmptyForNull() {
        List<String> errors = validator.validateActions(null);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for empty input")
    void shouldReturnEmptyForEmpty() {
        List<String> errors = validator.validateActions(List.of());
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should handle runtime exception in validation")
    void shouldHandleRuntimeException() {
        ActionHandler handler = mock(ActionHandler.class);
        doThrow(new RuntimeException("Unexpected error")).when(handler).validate(anyString());
        when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

        String error = validator.validateAction("FIELD_UPDATE", "not-json");

        assertNotNull(error);
        assertTrue(error.contains("validation failed"));
    }
}
