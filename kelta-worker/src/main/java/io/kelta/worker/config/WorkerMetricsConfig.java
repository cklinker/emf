package io.kelta.worker.config;

import io.kelta.worker.service.CollectionLifecycleManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registers custom worker metrics with Spring Boot Actuator via Micrometer.
 *
 * <p>Exposes the following gauges at {@code /actuator/metrics/kelta.worker.*}:
 * <ul>
 *   <li>{@code kelta.worker.collections.active} - number of active collections</li>
 *   <li>{@code kelta.worker.collection.count} - active collection count for HPA scaling (Prometheus: kelta_worker_collection_count)</li>
 *   <li>{@code kelta.worker.collections.initializing} - number currently initializing</li>
 *   <li>{@code kelta.worker.uptime.seconds} - JVM uptime in seconds</li>
 *   <li>{@code kelta.worker.heap.used.bytes} - current heap usage in bytes</li>
 *   <li>{@code kelta.worker.heap.max.bytes} - maximum heap size in bytes</li>
 * </ul>
 */
@Configuration
public class WorkerMetricsConfig {

    private final AtomicInteger initializingCount = new AtomicInteger(0);

    // Use ObjectProvider instead of @Lazy to break the circular dependency with
    // CollectionLifecycleManager. @Lazy creates a CGLIB proxy at runtime which
    // is not supported in GraalVM native images. ObjectProvider defers resolution
    // and is fully AOT-compatible.
    public WorkerMetricsConfig(MeterRegistry meterRegistry,
                                ObjectProvider<CollectionLifecycleManager> lifecycleManagerProvider) {
        CollectionLifecycleManager lifecycleManager = lifecycleManagerProvider.getObject();
        // Active collections gauge — reads live count from the lifecycle manager
        Gauge.builder("kelta.worker.collections.active", lifecycleManager,
                        CollectionLifecycleManager::getActiveCollectionCount)
                .description("Number of active collections hosted by this worker")
                .register(meterRegistry);

        // HPA collection count gauge — same value, exposed as kelta_worker_collection_count
        // in Prometheus format for Kubernetes HPA custom metrics (target: 40 per pod)
        Gauge.builder("kelta.worker.collection.count", lifecycleManager,
                        CollectionLifecycleManager::getActiveCollectionCount)
                .description("Collection count for HPA scaling (target: 40 per pod)")
                .register(meterRegistry);

        // Initializing collections gauge — tracks collections currently being initialized
        Gauge.builder("kelta.worker.collections.initializing", initializingCount, AtomicInteger::get)
                .description("Number of collections currently initializing")
                .register(meterRegistry);

        // JVM uptime in seconds
        Gauge.builder("kelta.worker.uptime.seconds", ManagementFactory.getRuntimeMXBean(),
                        bean -> bean.getUptime() / 1000.0)
                .description("JVM uptime in seconds")
                .register(meterRegistry);

        // Heap used bytes
        Gauge.builder("kelta.worker.heap.used.bytes", Runtime.getRuntime(),
                        rt -> rt.totalMemory() - rt.freeMemory())
                .description("Current heap memory usage in bytes")
                .register(meterRegistry);

        // Heap max bytes
        Gauge.builder("kelta.worker.heap.max.bytes", Runtime.getRuntime(),
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
