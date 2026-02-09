package com.emf.worker.config;

import com.emf.worker.service.CollectionLifecycleManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registers custom worker metrics with Spring Boot Actuator via Micrometer.
 *
 * <p>Exposes the following gauges at {@code /actuator/metrics/emf.worker.*}:
 * <ul>
 *   <li>{@code emf.worker.collections.active} - number of active collections</li>
 *   <li>{@code emf.worker.collection.count} - active collection count for HPA scaling (Prometheus: emf_worker_collection_count)</li>
 *   <li>{@code emf.worker.collections.initializing} - number currently initializing</li>
 *   <li>{@code emf.worker.uptime.seconds} - JVM uptime in seconds</li>
 *   <li>{@code emf.worker.heap.used.bytes} - current heap usage in bytes</li>
 *   <li>{@code emf.worker.heap.max.bytes} - maximum heap size in bytes</li>
 * </ul>
 */
@Configuration
public class WorkerMetricsConfig {

    private final AtomicInteger initializingCount = new AtomicInteger(0);

    public WorkerMetricsConfig(MeterRegistry meterRegistry,
                                @Lazy CollectionLifecycleManager lifecycleManager) {
        // Active collections gauge — reads live count from the lifecycle manager
        Gauge.builder("emf.worker.collections.active", lifecycleManager,
                        CollectionLifecycleManager::getActiveCollectionCount)
                .description("Number of active collections hosted by this worker")
                .register(meterRegistry);

        // HPA collection count gauge — same value, exposed as emf_worker_collection_count
        // in Prometheus format for Kubernetes HPA custom metrics (target: 40 per pod)
        Gauge.builder("emf.worker.collection.count", lifecycleManager,
                        CollectionLifecycleManager::getActiveCollectionCount)
                .description("Collection count for HPA scaling (target: 40 per pod)")
                .register(meterRegistry);

        // Initializing collections gauge — tracks collections currently being initialized
        Gauge.builder("emf.worker.collections.initializing", initializingCount, AtomicInteger::get)
                .description("Number of collections currently initializing")
                .register(meterRegistry);

        // JVM uptime in seconds
        Gauge.builder("emf.worker.uptime.seconds", ManagementFactory.getRuntimeMXBean(),
                        bean -> bean.getUptime() / 1000.0)
                .description("JVM uptime in seconds")
                .register(meterRegistry);

        // Heap used bytes
        Gauge.builder("emf.worker.heap.used.bytes", Runtime.getRuntime(),
                        rt -> rt.totalMemory() - rt.freeMemory())
                .description("Current heap memory usage in bytes")
                .register(meterRegistry);

        // Heap max bytes
        Gauge.builder("emf.worker.heap.max.bytes", Runtime.getRuntime(),
                        Runtime::maxMemory)
                .description("Maximum heap memory in bytes")
                .register(meterRegistry);
    }

    /**
     * Returns the atomic counter for collections currently initializing.
     * Used by {@link CollectionLifecycleManager} to increment/decrement during init.
     *
     * @return the initializing count
     */
    public AtomicInteger getInitializingCount() {
        return initializingCount;
    }
}
