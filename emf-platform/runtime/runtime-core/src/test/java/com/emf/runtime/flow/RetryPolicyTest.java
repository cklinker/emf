package com.emf.runtime.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetryPolicy")
class RetryPolicyTest {

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("matches specific error code")
        void matchesSpecificError() {
            RetryPolicy policy = new RetryPolicy(List.of("HttpTimeout", "Http5xx"), 1, 3, 2.0);
            assertTrue(policy.matches("HttpTimeout"));
            assertTrue(policy.matches("Http5xx"));
            assertFalse(policy.matches("ValidationError"));
        }

        @Test
        @DisplayName("States.ALL matches any error")
        void statesAllMatchesAny() {
            RetryPolicy policy = new RetryPolicy(List.of("States.ALL"), 1, 3, 2.0);
            assertTrue(policy.matches("HttpTimeout"));
            assertTrue(policy.matches("ValidationError"));
            assertTrue(policy.matches("anything"));
        }

        @Test
        @DisplayName("empty error list matches nothing")
        void emptyListMatchesNothing() {
            RetryPolicy policy = new RetryPolicy(List.of(), 1, 3, 2.0);
            assertFalse(policy.matches("HttpTimeout"));
        }

        @Test
        @DisplayName("null error list matches nothing")
        void nullListMatchesNothing() {
            RetryPolicy policy = new RetryPolicy(null, 1, 3, 2.0);
            assertFalse(policy.matches("HttpTimeout"));
        }
    }

    @Nested
    @DisplayName("delayMillis")
    class DelayMillis {

        @Test
        @DisplayName("first attempt uses base interval")
        void firstAttemptUsesBaseInterval() {
            RetryPolicy policy = new RetryPolicy(List.of("States.ALL"), 5, 3, 2.0);
            assertEquals(5000L, policy.delayMillis(1));
        }

        @Test
        @DisplayName("second attempt applies backoff rate")
        void secondAttemptAppliesBackoff() {
            RetryPolicy policy = new RetryPolicy(List.of("States.ALL"), 5, 3, 2.0);
            assertEquals(10000L, policy.delayMillis(2));
        }

        @Test
        @DisplayName("third attempt applies backoff squared")
        void thirdAttemptAppliesBackoffSquared() {
            RetryPolicy policy = new RetryPolicy(List.of("States.ALL"), 5, 3, 2.0);
            assertEquals(20000L, policy.delayMillis(3));
        }

        @Test
        @DisplayName("backoff rate of 1.0 keeps constant delay")
        void backoffRateOneKeepsConstant() {
            RetryPolicy policy = new RetryPolicy(List.of("States.ALL"), 3, 5, 1.0);
            assertEquals(3000L, policy.delayMillis(1));
            assertEquals(3000L, policy.delayMillis(2));
            assertEquals(3000L, policy.delayMillis(3));
        }
    }

    @Test
    @DisplayName("default constants are correct")
    void defaultConstants() {
        assertEquals(1, RetryPolicy.DEFAULT_INTERVAL_SECONDS);
        assertEquals(3, RetryPolicy.DEFAULT_MAX_ATTEMPTS);
        assertEquals(2.0, RetryPolicy.DEFAULT_BACKOFF_RATE);
    }
}
