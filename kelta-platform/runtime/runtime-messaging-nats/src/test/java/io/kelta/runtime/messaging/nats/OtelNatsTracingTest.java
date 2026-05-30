package io.kelta.runtime.messaging.nats;

import io.nats.client.impl.Headers;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OtelNatsTracing")
class OtelNatsTracingTest {

    private OpenTelemetry openTelemetry() {
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        return OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setPropagators(ContextPropagators.create(propagator))
                .build();
    }

    @Test
    @DisplayName("inject writes a W3C traceparent header derived from the current span")
    void injectWritesTraceparent() {
        OpenTelemetry otel = openTelemetry();
        OtelNatsTracing tracing = new OtelNatsTracing(otel);
        Tracer tracer = otel.getTracer("test");
        Span span = tracer.spanBuilder("publish").setSpanKind(SpanKind.PRODUCER).startSpan();
        Headers headers = new Headers();

        try (Scope ignored = span.makeCurrent()) {
            tracing.inject(headers);
        } finally {
            span.end();
        }

        String traceparent = headers.getFirst("traceparent");
        assertThat(traceparent).isNotNull();
        assertThat(traceparent).contains(span.getSpanContext().getTraceId());
    }

    @Test
    @DisplayName("extractAndOpen restores the span context written by inject")
    void extractRoundTrip() {
        OpenTelemetry otel = openTelemetry();
        OtelNatsTracing tracing = new OtelNatsTracing(otel);
        Tracer tracer = otel.getTracer("test");
        Span source = tracer.spanBuilder("publish").startSpan();
        Headers headers = new Headers();
        try (Scope ignored = source.makeCurrent()) {
            tracing.inject(headers);
        } finally {
            source.end();
        }

        SpanContext sourceCtx = source.getSpanContext();
        SpanContext extracted;
        try (NatsTracing.Scope ignored = tracing.extractAndOpen(headers)) {
            extracted = Span.fromContext(Context.current()).getSpanContext();
        }

        assertThat(extracted.getTraceId()).isEqualTo(sourceCtx.getTraceId());
        assertThat(extracted.getSpanId()).isEqualTo(sourceCtx.getSpanId());
    }

    @Test
    @DisplayName("extractAndOpen with empty headers returns an invalid context scope (no parent)")
    void extractEmptyHeaders() {
        OpenTelemetry otel = openTelemetry();
        OtelNatsTracing tracing = new OtelNatsTracing(otel);
        Headers headers = new Headers();

        SpanContext extracted;
        try (NatsTracing.Scope ignored = tracing.extractAndOpen(headers)) {
            extracted = Span.fromContext(Context.current()).getSpanContext();
        }

        assertThat(extracted.isValid()).isFalse();
    }
}
