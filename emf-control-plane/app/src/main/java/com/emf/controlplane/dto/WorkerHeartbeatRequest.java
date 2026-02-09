package com.emf.controlplane.dto;

import java.util.Map;

/**
 * Request DTO for worker heartbeat updates.
 */
public record WorkerHeartbeatRequest(
    int currentLoad,
    String status,
    Map<String, Object> metrics
) {}
