package com.emf.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

/**
 * Configuration properties for the EMF Worker Service.
 *
 * <p>Binds to the {@code emf.worker} prefix in application configuration.
 * Provides sensible defaults for all properties.
 */
@Component
@ConfigurationProperties(prefix = "emf.worker")
public class WorkerProperties {

    /**
     * Unique worker identifier. Defaults to a random UUID if not set.
     */
    private String id;

    /**
     * Worker pool name. Workers in the same pool can receive the same types of assignments.
     */
    private String pool = "default";

    /**
     * Maximum number of collections this worker can serve.
     */
    private int capacity = 50;

    /**
     * If set, this worker will only serve collections belonging to this tenant.
     */
    private String tenantAffinity;

    /**
     * Interval between heartbeat reports to the control plane, in milliseconds.
     */
    private long heartbeatInterval = 15000;

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

    public String getPool() {
        return pool;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getTenantAffinity() {
        return tenantAffinity;
    }

    public void setTenantAffinity(String tenantAffinity) {
        this.tenantAffinity = tenantAffinity;
    }

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public String getControlPlaneUrl() {
        return controlPlaneUrl;
    }

    public void setControlPlaneUrl(String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl;
    }

    /**
     * Returns the hostname of this worker, using the HOSTNAME environment variable
     * (set by Kubernetes) or falling back to the local hostname.
     *
     * @return the hostname
     */
    public String getHost() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
}
