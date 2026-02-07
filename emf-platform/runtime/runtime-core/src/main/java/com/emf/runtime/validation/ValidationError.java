package com.emf.runtime.validation;

/**
 * Represents a single validation rule violation.
 */
public record ValidationError(
        String ruleName,
        String errorMessage,
        String errorField
) {
}
