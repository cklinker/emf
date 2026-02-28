package com.emf.runtime.flow;

import java.util.List;

/**
 * Retry policy for Task states. When a task fails with a matching error,
 * the engine retries execution with exponential backoff.
 *
 * @param errorEquals      list of error codes that trigger this retry (e.g., "HttpTimeout", "States.ALL")
 * @param intervalSeconds  initial interval between retries in seconds (default: 1)
 * @param maxAttempts      maximum number of retry attempts (default: 3)
 * @param backoffRate      multiplier applied to the interval after each retry (default: 2.0)
 * @since 1.0.0
 */
public record RetryPolicy(
    List<String> errorEquals,
    int intervalSeconds,
    int maxAttempts,
    double backoffRate
) {

    /** Default interval between retries: 1 second. */
    public static final int DEFAULT_INTERVAL_SECONDS = 1;

    /** Default maximum retry attempts. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** Default backoff multiplier. */
    public static final double DEFAULT_BACKOFF_RATE = 2.0;

    /**
     * Returns true if this policy matches the given error code.
     * "States.ALL" matches any error.
     */
    public boolean matches(String errorCode) {
        if (errorEquals == null || errorEquals.isEmpty()) {
            return false;
        }
        return errorEquals.contains("States.ALL") || errorEquals.contains(errorCode);
    }

    /**
     * Calculates the delay in milliseconds for the given attempt number (1-based).
     */
    public long delayMillis(int attemptNumber) {
        double delay = intervalSeconds * 1000.0 * Math.pow(backoffRate, attemptNumber - 1);
        return (long) delay;
    }
}
