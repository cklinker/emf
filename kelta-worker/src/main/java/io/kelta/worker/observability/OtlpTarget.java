package io.kelta.worker.observability;

import java.util.Map;

/**
 * A tenant's OTLP trace-export destination: the collector endpoint plus any
 * static headers (e.g. an auth token) to send with each export.
 *
 * @param endpoint the OTLP/HTTP traces endpoint, e.g. {@code https://otlp.acme.example/v1/traces}
 * @param headers  headers attached to every export (may be empty)
 */
public record OtlpTarget(String endpoint, Map<String, String> headers) {

    public OtlpTarget {
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
}
