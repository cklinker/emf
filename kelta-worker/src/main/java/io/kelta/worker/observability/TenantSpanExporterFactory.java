package io.kelta.worker.observability;

import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * Builds the export pipeline (a batching {@link SpanProcessor} over an OTLP exporter)
 * for a tenant's {@link OtlpTarget}. Kept behind a seam so {@link PerTenantOtlpSpanProcessor}'s
 * routing logic is unit-testable without constructing real OTLP exporters.
 */
public interface TenantSpanExporterFactory {

    /**
     * @param target the tenant's OTLP destination
     * @return a {@link SpanProcessor} that exports spans to {@code target}
     */
    SpanProcessor create(OtlpTarget target);
}
