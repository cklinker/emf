package com.emf.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Configuration properties for the EMF Worker Service.
 *
 * <p>Binds to the {@code emf.worker} prefix in application configuration.
 * Provides sensible defaults for all properties.
 *
 * <p>Worker lifecycle (registration, heartbeat, assignments) has been removed.
 * Kubernetes manages pod health via liveness/readiness probes.
 */
@Component
@ConfigurationProperties(prefix = "emf.worker")
public class WorkerProperties {

    /**
     * Unique worker identifier. Defaults to a random UUID if not set.
     */
    private String id;

    /**
     * URL of the control plane service.
     */
    private String controlPlaneUrl = "http://localhost:8080";

    public String getId() {
        if (id == null || id.isBlank()) {
            id = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getControlPlaneUrl() {
        return controlPlaneUrl;
    }

    public void setControlPlaneUrl(String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl;
    }
}
