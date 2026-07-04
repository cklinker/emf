package io.kelta.worker.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-tenant OTLP trace export (Rec 7). An <em>additive</em> {@link SpanProcessor}:
 * on span end it reads the {@code kelta.tenant.id} attribute (stamped by
 * {@code SpanBodyEnrichmentFilter}) and, when that tenant has a configured OTLP
 * target ({@link TenantOtlpRegistry}), forwards the span to a per-tenant batching
 * exporter built lazily by {@link TenantSpanExporterFactory} and cached by tenant.
 *
 * <p>This never touches the platform's default export pipeline — spans always also
 * flow to the platform collector. A span with no tenant attribute, or a tenant with
 * no configured target, is simply not re-exported.
 *
 * <p>Each cached delegate remembers the {@link OtlpTarget} it was built for; when a
 * tenant re-points its endpoint (or changes headers) the stale delegate is shut down
 * and replaced on the next span — no restart required.
 */
@Component
public class PerTenantOtlpSpanProcessor implements SpanProcessor {

    private static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("kelta.tenant.id");

    private final TenantOtlpRegistry registry;
    private final TenantSpanExporterFactory exporterFactory;
    private final ConcurrentMap<String, TargetedDelegate> delegates = new ConcurrentHashMap<>();

    private record TargetedDelegate(OtlpTarget target, SpanProcessor processor) {
    }

    public PerTenantOtlpSpanProcessor(TenantOtlpRegistry registry, TenantSpanExporterFactory exporterFactory) {
        this.registry = registry;
        this.exporterFactory = exporterFactory;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // No-op: routing happens on end, once the tenant attribute is set.
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        String tenantId = span.getAttribute(TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        Optional<OtlpTarget> target = registry.targetFor(tenantId);
        if (target.isEmpty()) {
            return;
        }
        TargetedDelegate delegate = delegates.compute(tenantId, (t, existing) -> {
            if (existing != null && existing.target().equals(target.get())) {
                return existing;
            }
            if (existing != null) {
                existing.processor().shutdown();
            }
            return new TargetedDelegate(target.get(), exporterFactory.create(target.get()));
        });
        delegate.processor().onEnd(span);
    }

    @Override
    public CompletableResultCode forceFlush() {
        List<CompletableResultCode> results = delegates.values().stream()
                .map(d -> d.processor().forceFlush()).toList();
        return CompletableResultCode.ofAll(results);
    }

    @Override
    public CompletableResultCode shutdown() {
        List<CompletableResultCode> results = delegates.values().stream()
                .map(d -> d.processor().shutdown()).toList();
        return CompletableResultCode.ofAll(results);
    }
}
