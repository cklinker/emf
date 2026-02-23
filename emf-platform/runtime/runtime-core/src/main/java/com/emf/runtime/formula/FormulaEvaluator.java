package com.emf.runtime.formula;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Shared expression evaluator used by formula fields, validation rules,
 * rollup summaries, and workflow criteria.
 * <p>
 * Includes an in-memory compilation cache for parsed ASTs. When the same
 * formula expression is evaluated repeatedly (e.g., a workflow filter formula
 * evaluated for every record change event), the parsed AST is reused instead
 * of re-parsing each time. The cache is bounded to prevent unbounded memory
 * growth.
 */
@Service
public class FormulaEvaluator {

    private static final int DEFAULT_CACHE_MAX_SIZE = 1000;

    private final FormulaParser parser;
    private final Map<String, FormulaFunction> functions;
    private final ConcurrentHashMap<String, FormulaAst> compilationCache;
    private final int cacheMaxSize;

    @Autowired
    public FormulaEvaluator(List<FormulaFunction> functionList) {
        this(functionList, DEFAULT_CACHE_MAX_SIZE);
    }

    /**
     * Constructor with configurable cache size (useful for testing).
     */
    FormulaEvaluator(List<FormulaFunction> functionList, int cacheMaxSize) {
        this.parser = new FormulaParser();
        this.functions = functionList.stream()
                .collect(Collectors.toMap(f -> f.name().toUpperCase(), f -> f));
        this.compilationCache = new ConcurrentHashMap<>();
        this.cacheMaxSize = cacheMaxSize;
    }

    /**
     * Evaluates a formula expression against a record context.
     * <p>
     * Parsed ASTs are cached for reuse. The cache avoids re-parsing the same
     * expression on every evaluation, which is especially beneficial for
     * workflow filter formulas that are evaluated for every record change event.
     *
     * @param expression the formula string, e.g., "Amount * Quantity"
     * @param context    field values as a Map
     * @return the evaluation result (String, Number, Boolean, or null)
     */
    public Object evaluate(String expression, Map<String, Object> context) {
        FormulaAst ast = getOrParseAst(expression);
        return ast.evaluate(new FormulaContext(context, functions));
    }

    /**
     * Evaluates and returns a Boolean result. Used by validation rules
     * and workflow filter formulas.
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

    /**
     * Evicts a specific expression from the compilation cache.
     * <p>
     * Called when a workflow rule's filter formula changes, ensuring stale
     * cached ASTs are not reused.
     *
     * @param expression the formula expression to evict
     */
    public void evict(String expression) {
        compilationCache.remove(expression);
    }

    /**
     * Clears all cached compiled formulas.
     * <p>
     * Called when workflow rules are bulk-invalidated (e.g., on live reload
     * via Kafka event).
     */
    public void clearCache() {
        compilationCache.clear();
    }

    /**
     * Returns the current number of cached compiled formulas.
     */
    public int cacheSize() {
        return compilationCache.size();
    }

    /**
     * Gets a cached AST or parses and caches a new one.
     * <p>
     * If the cache exceeds the maximum size, it is cleared before adding new
     * entries. This is a simple eviction strategy that prevents unbounded growth
     * while maintaining simplicity. A more sophisticated LRU eviction could be
     * added if needed.
     */
    private FormulaAst getOrParseAst(String expression) {
        FormulaAst cached = compilationCache.get(expression);
        if (cached != null) {
            return cached;
        }

        FormulaAst ast = parser.parse(expression);

        // Simple bounded cache: clear when exceeding max size
        if (compilationCache.size() >= cacheMaxSize) {
            compilationCache.clear();
        }

        compilationCache.put(expression, ast);
        return ast;
    }
}
