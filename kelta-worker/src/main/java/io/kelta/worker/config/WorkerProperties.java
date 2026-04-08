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

    /**
     * Number of consecutive Cerbos failures before the circuit breaker opens.
     */
    private int cerbosCbThreshold = 3;

    /**
     * How long (in seconds) the Cerbos circuit breaker stays open after tripping.
     */
    private long cerbosCbCooldownSeconds = 10;

    public String getId() {
        if (id == null || id.isBlank()) {
            id = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCerbosCbThreshold() {
        return cerbosCbThreshold;
    }

    public void setCerbosCbThreshold(int cerbosCbThreshold) {
        this.cerbosCbThreshold = cerbosCbThreshold;
    }

    public long getCerbosCbCooldownSeconds() {
        return cerbosCbCooldownSeconds;
    }

    public void setCerbosCbCooldownSeconds(long cerbosCbCooldownSeconds) {
        this.cerbosCbCooldownSeconds = cerbosCbCooldownSeconds;
    }
}
