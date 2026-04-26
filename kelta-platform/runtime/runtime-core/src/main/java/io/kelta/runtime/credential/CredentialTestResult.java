package io.kelta.runtime.credential;

import java.util.Map;

/**
 * Outcome of a credential connectivity test.
 *
 * @param ok      whether the test succeeded
 * @param message human-readable status message; safe to render in the UI
 * @param details optional, non-secret diagnostic data (e.g., user identity returned by the provider).
 *                Never include credential material here — these details are returned to the
 *                client and persisted in {@code last_test_error} on failure.
 */
public record CredentialTestResult(
    boolean ok,
    String message,
    Map<String, Object> details
) {
    public static CredentialTestResult success(String message) {
        return new CredentialTestResult(true, message, Map.of());
    }

    public static CredentialTestResult success(String message, Map<String, Object> details) {
        return new CredentialTestResult(true, message, details);
    }

    public static CredentialTestResult failure(String message) {
        return new CredentialTestResult(false, message, Map.of());
    }
}
