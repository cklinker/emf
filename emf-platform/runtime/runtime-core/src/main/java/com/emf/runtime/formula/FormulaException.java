package com.emf.runtime.formula;

/**
 * Exception thrown when a formula expression fails to parse or evaluate.
 */
public class FormulaException extends RuntimeException {

    public FormulaException(String message) {
        super(message);
    }

    public FormulaException(String message, Throwable cause) {
        super(message, cause);
    }
}
