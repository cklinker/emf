package com.emf.worker.service;

import com.emf.worker.config.WorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core lifecycle manager for the worker service.
 *
 * <p>Handles:
 * <ul>
 *   <li><b>Startup:</b> Registers with the control plane, initializes assigned collections</li>
 *   <li><b>Heartbeat:</b> Periodically reports status and metrics to the control plane</li>
 *   <li><b>Shutdown:</b> Deregisters from the control plane gracefully</li>
 * </ul>
 *
 * <p>Uses retry logic for control plane communication (3 attempts, 2 second delay)
 * to handle startup timing issues.
 */
@Service
public class WorkerRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistrationService.class);

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final WorkerProperties workerProperties;
    private final RestTemplate restTemplate;
    private final CollectionLifecycleManager lifecycleManager;
    private final WorkerHealthReporter healthReporter;
    private final ObjectMapper objectMapper;

    private volatile String status = "STARTING";

    public WorkerRegistrationService(WorkerProperties workerProperties,
                                      RestTemplate restTemplate,
                                      CollectionLifecycleManager lifecycleManager,
                                      WorkerHealthReporter healthReporter,
                                      ObjectMapper objectMapper) {
        this.workerProperties = workerProperties;
        this.restTemplate = restTemplate;
        this.lifecycleManager = lifecycleManager;
        this.healthReporter = healthReporter;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers this worker with the control plane when the application is ready.
     *
     * <p>Includes retry logic to handle control plane startup timing.
     * After registration, initializes any pre-assigned collections and
     * sends a heartbeat to mark the worker as READY.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Worker '{}' starting registration with control plane at {}",
                workerProperties.getId(), workerProperties.getControlPlaneUrl());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Registration attempt {} of {}", attempt, MAX_RETRIES);

                // Build and send registration request
                List<String> assignedCollections = register();

                // Initialize any pre-assigned collections
                if (assignedCollections != null && !assignedCollections.isEmpty()) {
                    log.info("Received {} pre-assigned collections", assignedCollections.size());
                    for (String collectionId : assignedCollections) {
                        lifecycleManager.initializeCollection(collectionId);
                    }
                }

                // Mark as ready
                status = "READY";
                sendHeartbeat();

                log.info("Worker '{}' successfully registered and ready (pool={}, capacity={})",
                        workerProperties.getId(), workerProperties.getPool(),
                        workerProperties.getCapacity());
                return;

            } catch (Exception e) {
                log.warn("Registration attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    log.info("Retrying in {}ms...", RETRY_DELAY_MS);
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Registration retry interrupted");
                        return;
                    }
                } else {
                    log.error("Failed to register with control plane after {} attempts. " +
                            "Worker will continue without control plane registration.", MAX_RETRIES, e);
                    status = "READY";
                }
            }
        }
    }

    /**
     * Sends a periodic heartbeat to the control plane.
     * The interval is controlled by the {@code emf.worker.heartbeat-interval} property.
     */
    @Scheduled(fixedDelayString = "${emf.worker.heartbeat-interval:15000}")
    public void sendHeartbeat() {
        if ("STARTING".equals(status)) {
            return; // Don't send heartbeats until registered
        }

        try {
            String url = workerProperties.getControlPlaneUrl()
                    + "/control/workers/" + workerProperties.getId() + "/heartbeat";

            Map<String, Object> heartbeat = new HashMap<>();
            heartbeat.put("currentLoad", healthReporter.getCollectionCount());
            heartbeat.put("status", status);
            heartbeat.put("metrics", healthReporter.getMetrics());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(heartbeat, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.debug("Heartbeat sent: status={}, load={}/{}",
                    status, healthReporter.getCollectionCount(), workerProperties.getCapacity());

        } catch (Exception e) {
            log.warn("Failed to send heartbeat: {}", e.getMessage());
        }
    }

    /**
     * Deregisters this worker from the control plane on shutdown.
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Worker '{}' shutting down, deregistering from control plane", workerProperties.getId());
        status = "DRAINING";

        try {
            String url = workerProperties.getControlPlaneUrl()
                    + "/control/workers/" + workerProperties.getId() + "/deregister";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>("{}", headers);

            restTemplate.postForEntity(url, request, Void.class);
            log.info("Worker '{}' successfully deregistered", workerProperties.getId());

        } catch (Exception e) {
            log.warn("Failed to deregister worker '{}': {}", workerProperties.getId(), e.getMessage());
        }
    }

    /**
     * Returns the current worker status.
     *
     * @return the status string (STARTING, READY, or DRAINING)
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sends the registration request to the control plane and returns
     * any pre-assigned collection IDs.
     */
    @SuppressWarnings("unchecked")
    private List<String> register() {
        String url = workerProperties.getControlPlaneUrl() + "/control/workers/register";

        Map<String, Object> registration = new HashMap<>();
        registration.put("workerId", workerProperties.getId());
        registration.put("host", workerProperties.getHost());
        registration.put("port", getServerPort());
        registration.put("capacity", workerProperties.getCapacity());
        registration.put("pool", workerProperties.getPool());

        if (workerProperties.getTenantAffinity() != null && !workerProperties.getTenantAffinity().isBlank()) {
            registration.put("tenantAffinity", workerProperties.getTenantAffinity());
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("version", "1.0.0");
        registration.put("labels", labels);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(registration, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("assignedCollections")) {
            Object assigned = response.getBody().get("assignedCollections");
            if (assigned instanceof List<?> list) {
                return list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }
        }

        return List.of();
    }

    /**
     * Gets the server port from the SERVER_PORT environment variable or defaults to 8080.
     */
    private int getServerPort() {
        String portStr = System.getenv("SERVER_PORT");
        if (portStr != null && !portStr.isBlank()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return 8080;
    }
}
