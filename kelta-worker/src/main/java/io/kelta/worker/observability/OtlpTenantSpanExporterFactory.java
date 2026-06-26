package io.kelta.worker.observability;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.stereotype.Component;

/**
 * Builds a batching OTLP/HTTP export pipeline for a tenant's {@link OtlpTarget}.
 */
@Component
public class OtlpTenantSpanExporterFactory implements TenantSpanExporterFactory {

    @Override
    public SpanProcessor create(OtlpTarget target) {
        var builder = OtlpHttpSpanExporter.builder().setEndpoint(target.endpoint());
        target.headers().forEach(builder::addHeader);
        return BatchSpanProcessor.builder(builder.build()).build();
    }
}
