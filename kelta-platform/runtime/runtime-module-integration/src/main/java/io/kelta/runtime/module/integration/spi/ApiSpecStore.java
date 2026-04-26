package io.kelta.runtime.module.integration.spi;

import io.kelta.runtime.module.integration.api.ApiOperation;
import io.kelta.runtime.module.integration.api.ApiSpec;

import java.util.Optional;

/**
 * SPI for looking up imported OpenAPI specs and their operations at runtime.
 * The worker provides a JDBC-backed implementation; integration handlers
 * (PR 4's {@code CALL_API}) consume this to translate
 * {@code (specId, operationId)} into URL/method/schemas without re-parsing
 * the raw spec on every invocation.
 */
public interface ApiSpecStore {

    /** Looks up a spec by id within the supplied tenant. */
    Optional<ApiSpec> findSpec(String tenantId, String specId);

    /**
     * Looks up an operation by its {@code synthetic_op_id} within a given spec.
     * Returns empty when either the spec or the operation does not exist.
     */
    Optional<ApiOperation> findOperation(String tenantId, String specId, String syntheticOpId);
}
