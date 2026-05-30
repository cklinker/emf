package io.kelta.runtime.messaging.nats;

import io.nats.client.impl.Headers;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * OpenTelemetry-backed implementation of {@link NatsTracing}. Uses the configured
 * {@link io.opentelemetry.context.propagation.ContextPropagators} (typically W3C
 * {@code traceparent} + {@code tracestate}) to write outbound headers and read
 * inbound ones.
 *
 * <p>Loaded automatically by {@link NatsAutoConfiguration} when an
 * {@code OpenTelemetry} bean is available. Direct construction is also valid for
 * tests.
 */
public final class OtelNatsTracing implements NatsTracing {

    private static final TextMapSetter<Headers> SETTER = (carrier, key, value) -> {
        if (carrier != null) {
            carrier.put(key, value);
        }
    };

    private static final TextMapGetter<Headers> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers carrier) {
            return carrier == null ? java.util.Collections.emptyList() : carrier.keySet();
        }

        @Override
        public String get(Headers carrier, String key) {
            if (carrier == null) {
                return null;
            }
            return carrier.getFirst(key);
        }
    };

    private final TextMapPropagator propagator;

    public OtelNatsTracing(OpenTelemetry openTelemetry) {
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    @Override
    public void inject(Headers headers) {
        propagator.inject(Context.current(), headers, SETTER);
    }

    @Override
    public Scope extractAndOpen(Headers headers) {
        Context extracted = propagator.extract(Context.current(), headers, GETTER);
        io.opentelemetry.context.Scope otelScope = extracted.makeCurrent();
        return otelScope::close;
    }
}
