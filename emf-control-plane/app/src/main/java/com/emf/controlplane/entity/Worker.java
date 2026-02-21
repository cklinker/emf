package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * Represents a worker instance in the EMF platform.
 * A worker is a runtime process that hosts and serves collections.
 * Workers register with the control plane, send heartbeats, and receive collection assignments.
 */
@Entity
@Table(name = "worker")
public class Worker extends TenantScopedEntity {

    @Column(name = "pod_name", length = 253)
    private String podName;

    @Column(name = "namespace", length = 63)
    private String namespace;

    @Column(name = "host", nullable = false, length = 253)
    private String host;

    @Column(name = "port", nullable = false)
    private int port = 8080;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "pool", nullable = false, length = 50)
    private String pool = "default";

    @Column(name = "capacity", nullable = false)
    private int capacity = 50;

    @Column(name = "current_load", nullable = false)
    private int currentLoad = 0;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "STARTING";

    @Column(name = "tenant_affinity", length = 36)
    private String tenantAffinity;

    @Column(name = "labels", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String labels;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    public Worker() {
        super("default");
    }

    public Worker(String host, int port, String baseUrl) {
        super("default");
        this.host = host;
        this.port = port;
        this.baseUrl = baseUrl;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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

    public int getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(int currentLoad) {
        this.currentLoad = currentLoad;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTenantAffinity() {
        return tenantAffinity;
    }

    public void setTenantAffinity(String tenantAffinity) {
        this.tenantAffinity = tenantAffinity;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    @Override
    public String toString() {
        return "Worker{" +
                "id='" + getId() + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", pool='" + pool + '\'' +
                ", status='" + status + '\'' +
                ", currentLoad=" + currentLoad +
                ", capacity=" + capacity +
                '}';
    }
}
