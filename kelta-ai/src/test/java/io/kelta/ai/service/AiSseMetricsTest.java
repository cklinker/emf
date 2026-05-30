package io.kelta.ai.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiSseMetrics")
class AiSseMetricsTest {

    private MeterRegistry registry;
    private AiSseMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiSseMetrics(registry);
    }

    @Test
    @DisplayName("streamOpened increments active gauge and opened counter")
    void streamOpened() {
        metrics.streamOpened();
        metrics.streamOpened();

        assertThat(metrics.activeStreamCount()).isEqualTo(2);
        assertThat(registry.find("kelta.ai.sse.opened").counter().count()).isEqualTo(2.0);
        assertThat(registry.find("kelta.ai.sse.active").gauge().value()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("streamClosed decrements gauge and increments closed counter tagged by reason")
    void streamClosed() {
        metrics.streamOpened();
        metrics.streamOpened();
        metrics.streamOpened();
        metrics.streamClosed(AiSseMetrics.CloseReason.COMPLETION);
        metrics.streamClosed(AiSseMetrics.CloseReason.TIMEOUT);
        metrics.streamClosed(AiSseMetrics.CloseReason.ERROR);

        assertThat(metrics.activeStreamCount()).isZero();
        assertThat(registry.find("kelta.ai.sse.closed").tag("reason", "completion").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("kelta.ai.sse.closed").tag("reason", "timeout").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("kelta.ai.sse.closed").tag("reason", "error").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("all close-reason counters registered up front for stable dashboards")
    void closeReasonsPreregistered() {
        for (AiSseMetrics.CloseReason reason : AiSseMetrics.CloseReason.values()) {
            assertThat(registry.find("kelta.ai.sse.closed").tag("reason", reason.tag()).counter())
                    .as("counter for reason=%s should exist before any close happens", reason.tag())
                    .isNotNull();
        }
    }
}
