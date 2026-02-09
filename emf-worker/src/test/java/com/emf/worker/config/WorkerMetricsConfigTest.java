package com.emf.worker.config;

import com.emf.worker.service.CollectionLifecycleManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WorkerMetricsConfig}.
 */
class WorkerMetricsConfigTest {

    private MeterRegistry meterRegistry;
    private CollectionLifecycleManager lifecycleManager;
    private WorkerMetricsConfig metricsConfig;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lifecycleManager = mock(CollectionLifecycleManager.class);
        when(lifecycleManager.getActiveCollectionCount()).thenReturn(5);

        metricsConfig = new WorkerMetricsConfig(meterRegistry, lifecycleManager);
    }

    @Test
    void shouldRegisterActiveCollectionsGauge() {
        Gauge gauge = meterRegistry.find("emf.worker.collections.active").gauge();
        assertNotNull(gauge, "emf.worker.collections.active gauge should be registered");
        assertEquals(5.0, gauge.value(), "Should reflect lifecycle manager collection count");
    }

    @Test
    void shouldRegisterInitializingCollectionsGauge() {
        Gauge gauge = meterRegistry.find("emf.worker.collections.initializing").gauge();
        assertNotNull(gauge, "emf.worker.collections.initializing gauge should be registered");
        assertEquals(0.0, gauge.value(), "Should start at zero");
    }

    @Test
    void shouldTrackInitializingCountChanges() {
        metricsConfig.getInitializingCount().incrementAndGet();
        metricsConfig.getInitializingCount().incrementAndGet();

        Gauge gauge = meterRegistry.find("emf.worker.collections.initializing").gauge();
        assertNotNull(gauge);
        assertEquals(2.0, gauge.value(), "Should reflect incremented count");

        metricsConfig.getInitializingCount().decrementAndGet();
        assertEquals(1.0, gauge.value(), "Should reflect decremented count");
    }

    @Test
    void shouldRegisterUptimeGauge() {
        Gauge gauge = meterRegistry.find("emf.worker.uptime.seconds").gauge();
        assertNotNull(gauge, "emf.worker.uptime.seconds gauge should be registered");
        assertTrue(gauge.value() >= 0, "Uptime should be non-negative");
    }

    @Test
    void shouldRegisterHeapUsedGauge() {
        Gauge gauge = meterRegistry.find("emf.worker.heap.used.bytes").gauge();
        assertNotNull(gauge, "emf.worker.heap.used.bytes gauge should be registered");
        assertTrue(gauge.value() > 0, "Heap used should be positive");
    }

    @Test
    void shouldRegisterHeapMaxGauge() {
        Gauge gauge = meterRegistry.find("emf.worker.heap.max.bytes").gauge();
        assertNotNull(gauge, "emf.worker.heap.max.bytes gauge should be registered");
        assertTrue(gauge.value() > 0, "Heap max should be positive");
    }

    @Test
    void shouldUpdateActiveCollectionsWhenCountChanges() {
        when(lifecycleManager.getActiveCollectionCount()).thenReturn(10);

        Gauge gauge = meterRegistry.find("emf.worker.collections.active").gauge();
        assertNotNull(gauge);
        assertEquals(10.0, gauge.value(), "Should reflect updated collection count");
    }

    @Test
    void shouldExposeInitializingCountAtomicInteger() {
        assertNotNull(metricsConfig.getInitializingCount(),
                "getInitializingCount() should return the AtomicInteger");
        assertEquals(0, metricsConfig.getInitializingCount().get(),
                "Initial value should be 0");
    }
}
