package com.emf.controlplane.service.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActionResultTest {

    @Test
    @DisplayName("Success factory should create successful result")
    void successFactory() {
        ActionResult result = ActionResult.success();

        assertTrue(result.successful());
        assertNull(result.errorMessage());
        assertTrue(result.outputData().isEmpty());
    }

    @Test
    @DisplayName("Success with output data should include data")
    void successWithOutput() {
        Map<String, Object> output = Map.of("recordId", "abc-123");
        ActionResult result = ActionResult.success(output);

        assertTrue(result.successful());
        assertNull(result.errorMessage());
        assertEquals("abc-123", result.outputData().get("recordId"));
    }

    @Test
    @DisplayName("Failure factory should create failed result with message")
    void failureWithMessage() {
        ActionResult result = ActionResult.failure("Something went wrong");

        assertFalse(result.successful());
        assertEquals("Something went wrong", result.errorMessage());
        assertTrue(result.outputData().isEmpty());
    }

    @Test
    @DisplayName("Failure from exception should use exception message")
    void failureFromException() {
        ActionResult result = ActionResult.failure(new RuntimeException("Connection timeout"));

        assertFalse(result.successful());
        assertEquals("Connection timeout", result.errorMessage());
    }
}
