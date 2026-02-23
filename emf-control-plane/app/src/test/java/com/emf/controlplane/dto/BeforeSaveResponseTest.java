package com.emf.controlplane.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BeforeSaveResponse Tests")
class BeforeSaveResponseTest {

    @Test
    @DisplayName("No-arg constructor should create empty response")
    void noArgConstructor() {
        BeforeSaveResponse response = new BeforeSaveResponse();

        assertNull(response.getFieldUpdates());
        assertEquals(0, response.getRulesEvaluated());
        assertEquals(0, response.getActionsExecuted());
        assertNull(response.getErrors());
        assertFalse(response.hasErrors());
    }

    @Test
    @DisplayName("Three-arg constructor should set fields without errors")
    void threeArgConstructor() {
        BeforeSaveResponse response = new BeforeSaveResponse(
                Map.of("status", "ACTIVE"), 3, 2);

        assertEquals("ACTIVE", response.getFieldUpdates().get("status"));
        assertEquals(3, response.getRulesEvaluated());
        assertEquals(2, response.getActionsExecuted());
        assertNull(response.getErrors());
        assertFalse(response.hasErrors());
    }

    @Test
    @DisplayName("Four-arg constructor should set fields with errors")
    void fourArgConstructor() {
        List<Map<String, String>> errors = List.of(
                Map.of("field", "email", "message", "Email is required"));

        BeforeSaveResponse response = new BeforeSaveResponse(
                Map.of(), 0, 0, errors);

        assertTrue(response.hasErrors());
        assertEquals(1, response.getErrors().size());
        assertEquals("email", response.getErrors().get(0).get("field"));
    }

    @Test
    @DisplayName("hasErrors should return false for null errors")
    void hasErrorsFalseForNull() {
        BeforeSaveResponse response = new BeforeSaveResponse(
                Map.of(), 0, 0, null);

        assertFalse(response.hasErrors());
    }

    @Test
    @DisplayName("hasErrors should return false for empty errors list")
    void hasErrorsFalseForEmpty() {
        BeforeSaveResponse response = new BeforeSaveResponse(
                Map.of(), 0, 0, List.of());

        assertFalse(response.hasErrors());
    }

    @Test
    @DisplayName("hasErrors should return true for non-empty errors list")
    void hasErrorsTrueForNonEmpty() {
        List<Map<String, String>> errors = List.of(
                Map.of("field", "name", "message", "Required"));

        BeforeSaveResponse response = new BeforeSaveResponse(
                Map.of(), 0, 0, errors);

        assertTrue(response.hasErrors());
    }

    @Test
    @DisplayName("Setters should work for all fields")
    void settersShouldWork() {
        BeforeSaveResponse response = new BeforeSaveResponse();

        response.setFieldUpdates(Map.of("status", "PENDING"));
        response.setRulesEvaluated(5);
        response.setActionsExecuted(3);
        response.setErrors(List.of(Map.of("field", "a", "message", "b")));

        assertEquals("PENDING", response.getFieldUpdates().get("status"));
        assertEquals(5, response.getRulesEvaluated());
        assertEquals(3, response.getActionsExecuted());
        assertTrue(response.hasErrors());
    }
}
