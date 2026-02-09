package com.emf.worker.service;

import com.emf.runtime.registry.CollectionRegistry;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides health and metrics data for the worker heartbeat.
 *
 * <p>Collects runtime metrics including:
 * <ul>
 *   <li>Number of active collections hosted by this worker</li>
 *   <li>JVM uptime in milliseconds</li>
 *   <li>Current heap memory usage</li>
 * </ul>
 */
@Component
public class WorkerHealthReporter {

    private final CollectionRegistry registry;

    public WorkerHealthReporter(CollectionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the number of collections currently registered in this worker.
     *
     * @return the collection count
     */
    public int getCollectionCount() {
        return registry.getAllCollectionNames().size();
    }

    /**
     * Collects current worker metrics for the heartbeat payload.
     *
     * @return a map of metric names to values
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("collectionCount", getCollectionCount());
        metrics.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        metrics.put("heapUsed", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        metrics.put("heapMax", Runtime.getRuntime().maxMemory());
        metrics.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        return metrics;
    }
}
