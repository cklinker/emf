package com.emf.runtime.validation;

import java.util.List;

/**
 * Exception thrown when record data fails validation rules.
 */
public class RecordValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public RecordValidationException(List<ValidationError> errors) {
        super("Record validation failed: " + errors.size() + " rule(s) violated");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
