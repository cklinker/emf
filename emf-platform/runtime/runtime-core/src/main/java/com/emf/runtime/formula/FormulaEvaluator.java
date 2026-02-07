package com.emf.runtime.formula;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared expression evaluator used by formula fields, validation rules,
 * rollup summaries, and workflow criteria.
 */
@Service
public class FormulaEvaluator {

    private final FormulaParser parser;
    private final Map<String, FormulaFunction> functions;

    public FormulaEvaluator(List<FormulaFunction> functionList) {
        this.parser = new FormulaParser();
        this.functions = functionList.stream()
                .collect(Collectors.toMap(f -> f.name().toUpperCase(), f -> f));
    }

    /**
     * Evaluates a formula expression against a record context.
     *
     * @param expression the formula string, e.g., "Amount * Quantity"
     * @param context    field values as a Map
     * @return the evaluation result (String, Number, Boolean, or null)
     */
    public Object evaluate(String expression, Map<String, Object> context) {
        FormulaAst ast = parser.parse(expression);
        return ast.evaluate(new FormulaContext(context, functions));
    }

    /**
     * Evaluates and returns a Boolean result. Used by validation rules.
     */
    public boolean evaluateBoolean(String expression, Map<String, Object> context) {
        Object result = evaluate(expression, context);
        if (result instanceof Boolean b) return b;
        throw new FormulaException("Expression did not evaluate to Boolean: " + expression);
    }

    /**
     * Validates that an expression is syntactically correct.
     *
     * @throws FormulaException if expression has syntax errors
     */
    public void validate(String expression) {
        parser.parse(expression);
    }
}
