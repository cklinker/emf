package com.emf.runtime.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CatchPolicy")
class CatchPolicyTest {

    @Test
    @DisplayName("matches specific error code")
    void matchesSpecificError() {
        CatchPolicy policy = new CatchPolicy(List.of("HttpTimeout", "Http5xx"), "$.error", "HandleError");
        assertTrue(policy.matches("HttpTimeout"));
        assertTrue(policy.matches("Http5xx"));
        assertFalse(policy.matches("ValidationError"));
    }

    @Test
    @DisplayName("States.ALL matches any error")
    void statesAllMatchesAny() {
        CatchPolicy policy = new CatchPolicy(List.of("States.ALL"), "$.error", "HandleError");
        assertTrue(policy.matches("HttpTimeout"));
        assertTrue(policy.matches("anything"));
    }

    @Test
    @DisplayName("empty error list matches nothing")
    void emptyListMatchesNothing() {
        CatchPolicy policy = new CatchPolicy(List.of(), "$.error", "HandleError");
        assertFalse(policy.matches("HttpTimeout"));
    }

    @Test
    @DisplayName("null error list matches nothing")
    void nullListMatchesNothing() {
        CatchPolicy policy = new CatchPolicy(null, "$.error", "HandleError");
        assertFalse(policy.matches("HttpTimeout"));
    }

    @Test
    @DisplayName("record fields are accessible")
    void recordFieldsAccessible() {
        CatchPolicy policy = new CatchPolicy(List.of("States.ALL"), "$.error", "FallbackState");
        assertEquals("$.error", policy.resultPath());
        assertEquals("FallbackState", policy.next());
        assertEquals(List.of("States.ALL"), policy.errorEquals());
    }
}
