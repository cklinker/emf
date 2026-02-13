package com.emf.controlplane.dto;

import java.util.Map;

/**
 * Request DTO for registering a worker with the control plane.
 */
public record WorkerRegistrationRequest(
    String workerId,
    String podName,
    String namespace,
    String host,
    String hostIp,
    int port,
    int capacity,
    String pool,
    String tenantAffinity,
    Map<String, String> labels
) {}
