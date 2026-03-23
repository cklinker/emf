package io.kelta.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Configuration properties for the Kelta Worker Service.
 *
 * <p>Binds to the {@code kelta.worker} prefix in application configuration.
 * Provides sensible defaults for all properties.
 *
 * <p>Worker lifecycle (registration, heartbeat, assignments) has been removed.
 * Kubernetes manages pod health via liveness/readiness probes.
 *
 * <p>The worker reads collection definitions directly from the shared database,
 * so no control plane URL is needed.
 */
@Component
@ConfigurationProperties(prefix = "kelta.worker")
public class WorkerProperties {

    /**
     * Unique worker identifier. Defaults to a random UUID if not set.
     */
    private String id;

    public String getId() {
        if (id == null || id.isBlank()) {
            id = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
