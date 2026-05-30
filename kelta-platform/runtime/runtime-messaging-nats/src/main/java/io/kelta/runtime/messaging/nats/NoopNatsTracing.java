package io.kelta.runtime.messaging.nats;

import io.nats.client.impl.Headers;

/** No-op tracing — used when OpenTelemetry is not on the classpath / not configured. */
final class NoopNatsTracing implements NatsTracing {

    @Override
    public void inject(Headers headers) {
        // intentionally empty
    }

    @Override
    public Scope extractAndOpen(Headers headers) {
        return Scope.NOOP;
    }
}
