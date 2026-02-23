package com.emf.runtime.workflow.module;

import com.emf.runtime.formula.FormulaEvaluator;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.registry.CollectionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Context provided to modules during startup and lifecycle operations.
 * Contains references to core runtime services that modules can use.
 *
 * @param queryEngine the query engine for CRUD operations
 * @param collectionRegistry the collection registry for looking up definitions
 * @param formulaEvaluator the formula evaluator (may be null)
 * @param objectMapper the Jackson object mapper for JSON processing
 *
 * @since 1.0.0
 */
public record ModuleContext(
    QueryEngine queryEngine,
    CollectionRegistry collectionRegistry,
    FormulaEvaluator formulaEvaluator,
    ObjectMapper objectMapper
) {
}
