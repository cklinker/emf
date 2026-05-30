package io.kelta.runtime.messaging.nats;

import io.nats.client.impl.Headers;

/**
 * SPI for propagating distributed-tracing context through NATS messages. Implementations
 * inject the current trace context into outbound message headers and extract it from
 * inbound headers to attach the receiving span to its parent span on the publisher.
 *
 * <p>The default {@link NoopNatsTracing} disables propagation; the OpenTelemetry-backed
 * implementation is wired automatically when an {@code OpenTelemetry} bean is present in
 * the application context (see {@link NatsAutoConfiguration}).
 */
public interface NatsTracing {

    NatsTracing NOOP = new NoopNatsTracing();

    /**
     * Inject the current outgoing trace context into the supplied NATS headers
     * (e.g. {@code traceparent}, {@code tracestate} for W3C).
     */
    void inject(Headers headers);

    /**
     * Extract the incoming trace context from NATS headers and open a scope that
     * makes it current on the calling thread. Callers must close the returned
     * scope when the handler completes.
     */
    Scope extractAndOpen(Headers headers);

    /**
     * A scope returned by {@link #extractAndOpen(Headers)}. Closing the scope
     * restores the previously-current context. Never null.
     */
    interface Scope extends AutoCloseable {
        Scope NOOP = () -> {};

        @Override
        void close();
    }
}
