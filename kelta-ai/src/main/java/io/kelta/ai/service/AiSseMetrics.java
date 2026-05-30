package io.kelta.ai.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE-streaming observability for the AI chat endpoint. Exposes:
 * <ul>
 *   <li>{@code kelta.ai.sse.active} — gauge of currently-open SSE emitters
 *   <li>{@code kelta.ai.sse.opened} — counter of total streams opened
 *   <li>{@code kelta.ai.sse.closed} — counter of streams closed, tagged
 *       {@code reason=completion|timeout|error}
 * </ul>
 *
 * <p>No per-tenant tagging — cardinality is bounded by close-reason enum.
 */
@Service
public class AiSseMetrics {

    public enum CloseReason {
        COMPLETION, TIMEOUT, ERROR;

        public String tag() {
            return name().toLowerCase();
        }
    }

    private final AtomicInteger activeStreams = new AtomicInteger();
    private final Counter openedCounter;

    public AiSseMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("kelta.ai.sse.active", activeStreams, AtomicInteger::get)
                .description("Current number of open AI SSE streams")
                .register(meterRegistry);
        this.openedCounter = Counter.builder("kelta.ai.sse.opened")
                .description("Total AI SSE streams opened")
                .register(meterRegistry);
        for (CloseReason reason : CloseReason.values()) {
            Counter.builder("kelta.ai.sse.closed")
                    .description("Total AI SSE streams closed by reason")
                    .tags(Tags.of("reason", reason.tag()))
                    .register(meterRegistry);
        }
        this.meterRegistry = meterRegistry;
    }

    private final MeterRegistry meterRegistry;

    public void streamOpened() {
        activeStreams.incrementAndGet();
        openedCounter.increment();
    }

    public void streamClosed(CloseReason reason) {
        activeStreams.decrementAndGet();
        meterRegistry.counter("kelta.ai.sse.closed", "reason", reason.tag()).increment();
    }

    int activeStreamCount() {
        return activeStreams.get();
    }
}
