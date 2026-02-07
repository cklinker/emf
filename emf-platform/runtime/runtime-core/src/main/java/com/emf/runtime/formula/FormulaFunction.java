package com.emf.runtime.formula;

import java.util.List;

/**
 * Interface for built-in formula functions (TODAY, LEN, IF, etc.).
 */
public interface FormulaFunction {

    /**
     * The function name (e.g., "TODAY", "LEN", "IF").
     */
    String name();

    /**
     * Executes the function with the given arguments.
     *
     * @param args     evaluated argument values
     * @param context  the formula context for field value lookups
     * @return the function result
     */
    Object execute(List<Object> args, FormulaContext context);
}
