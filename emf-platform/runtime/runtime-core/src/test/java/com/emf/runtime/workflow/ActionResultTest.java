package com.emf.runtime.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActionResult")
class ActionResultTest {

    @Test
    @DisplayName("Should create success result")
    void shouldCreateSuccessResult() {
        ActionResult result = ActionResult.success();
        assertTrue(result.successful());
        assertNull(result.errorMessage());
        assertTrue(result.outputData().isEmpty());
    }

    @Test
    @DisplayName("Should create success result with output data")
    void shouldCreateSuccessWithOutput() {
        Map<String, Object> output = Map.of("key", "value");
        ActionResult result = ActionResult.success(output);
        assertTrue(result.successful());
        assertNull(result.errorMessage());
        assertEquals("value", result.outputData().get("key"));
    }

    @Test
    @DisplayName("Should create failure result with message")
    void shouldCreateFailureResult() {
        ActionResult result = ActionResult.failure("Something went wrong");
        assertFalse(result.successful());
        assertEquals("Something went wrong", result.errorMessage());
        assertTrue(result.outputData().isEmpty());
    }

    @Test
    @DisplayName("Should create failure result from exception")
    void shouldCreateFailureFromException() {
        ActionResult result = ActionResult.failure(new RuntimeException("Error!"));
        assertFalse(result.successful());
        assertEquals("Error!", result.errorMessage());
    }
}
