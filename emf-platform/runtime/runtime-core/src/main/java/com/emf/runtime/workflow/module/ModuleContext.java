package com.emf.runtime.workflow.module;

import com.emf.runtime.formula.FormulaEvaluator;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Context provided to modules during startup and lifecycle operations.
 * Contains references to core runtime services that modules can use.
 *
 * <p>Modules can access well-known services via dedicated accessor methods
 * (e.g., {@link #queryEngine()}, {@link #objectMapper()}), or retrieve
 * custom extension services via {@link #getExtension(Class)}.
 *
 * @param queryEngine the query engine for CRUD operations
 * @param collectionRegistry the collection registry for looking up definitions
 * @param formulaEvaluator the formula evaluator (may be null)
 * @param objectMapper the Jackson object mapper for JSON processing
 * @param actionHandlerRegistry the action handler registry (may be null)
 * @param extensions custom extension services keyed by class type
 *
 * @since 1.0.0
 */
public record ModuleContext(
    QueryEngine queryEngine,
    CollectionRegistry collectionRegistry,
    FormulaEvaluator formulaEvaluator,
    ObjectMapper objectMapper,
    ActionHandlerRegistry actionHandlerRegistry,
    Map<Class<?>, Object> extensions
) {

    /**
     * Compact constructor that ensures extensions is never null and is unmodifiable.
     */
    public ModuleContext {
        extensions = extensions == null
            ? Map.of()
            : Collections.unmodifiableMap(new HashMap<>(extensions));
    }

    /**
     * Backward-compatible constructor for existing callers that don't need
     * actionHandlerRegistry or extensions.
     *
     * @param queryEngine the query engine for CRUD operations
     * @param collectionRegistry the collection registry for looking up definitions
     * @param formulaEvaluator the formula evaluator (may be null)
     * @param objectMapper the Jackson object mapper for JSON processing
     */
    public ModuleContext(QueryEngine queryEngine,
                         CollectionRegistry collectionRegistry,
                         FormulaEvaluator formulaEvaluator,
                         ObjectMapper objectMapper) {
        this(queryEngine, collectionRegistry, formulaEvaluator, objectMapper, null, Map.of());
    }

    /**
     * Retrieves an extension service by its type.
     *
     * @param type the class of the extension service
     * @param <T> the extension type
     * @return the extension service, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(Class<T> type) {
        return (T) extensions.get(type);
    }
}
