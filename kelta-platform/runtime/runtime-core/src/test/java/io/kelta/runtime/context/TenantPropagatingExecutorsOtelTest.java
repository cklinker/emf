package io.kelta.runtime.context;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link TenantPropagatingExecutors} propagates the
 * OpenTelemetry {@code Context} alongside tenant binding. Without this, a
 * span started on the worker thread would be a root span instead of a child
 * of the submitter's request span.
 */
@DisplayName("TenantPropagatingExecutors OTel context propagation")
class TenantPropagatingExecutorsOtelTest {

    private OpenTelemetry otel() {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .build();
    }

    @Test
    @DisplayName("captures the current OTel Context and applies it on the worker thread")
    void propagatesContext() throws Exception {
        OpenTelemetry sdk = otel();
        Tracer tracer = sdk.getTracer("test");
        Span parent = tracer.spanBuilder("parent").startSpan();
        SpanContext parentCtx = parent.getSpanContext();

        AtomicReference<SpanContext> seenOnWorker = new AtomicReference<>();

        try (Scope ignored = parent.makeCurrent()) {
            Runnable task = TenantPropagatingExecutors.wrap(() ->
                    seenOnWorker.set(Span.fromContext(Context.current()).getSpanContext()));

            // Run the wrapped task on a fresh thread to prove the captured
            // context travels independently of the caller's current scope.
            Thread t = new Thread(task);
            t.start();
            t.join(2000);
        } finally {
            parent.end();
        }

        assertThat(seenOnWorker.get()).isNotNull();
        assertThat(seenOnWorker.get().getTraceId()).isEqualTo(parentCtx.getTraceId());
        assertThat(seenOnWorker.get().getSpanId()).isEqualTo(parentCtx.getSpanId());
    }

    @Test
    @DisplayName("no current OTel span — worker also has no current span (no NPE)")
    void noCurrentContextIsSafe() throws Exception {
        AtomicReference<SpanContext> seenOnWorker = new AtomicReference<>();

        Runnable task = TenantPropagatingExecutors.wrap(() ->
                seenOnWorker.set(Span.fromContext(Context.current()).getSpanContext()));

        Thread t = new Thread(task);
        t.start();
        t.join(2000);

        assertThat(seenOnWorker.get()).isNotNull();
        assertThat(seenOnWorker.get().isValid()).isFalse();
    }
}
