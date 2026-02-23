package com.emf.worker.service;

import com.emf.worker.config.WorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Bootstraps the worker by loading all active collections from the control plane on startup.
 *
 * <p>On {@link ApplicationReadyEvent}, fetches the list of active collections from the
 * control plane's {@code /control/bootstrap} endpoint and initializes each one via
 * {@link CollectionLifecycleManager}. This ensures every worker can serve every collection,
 * allowing the K8s Service to load-balance requests across all worker pods.
 *
 * <p>There is no registration, heartbeat, or assignment tracking. Kubernetes manages
 * pod health via liveness/readiness probes. Runtime schema changes are handled by the
 * existing Kafka {@code collection-changed} listener.
 *
 * @since 1.0.0
 */
@Component
public class WorkerBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(WorkerBootstrapService.class);

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final WorkerProperties workerProperties;
    private final RestTemplate restTemplate;
    private final CollectionLifecycleManager lifecycleManager;

    public WorkerBootstrapService(WorkerProperties workerProperties,
                                   RestTemplate restTemplate,
                                   CollectionLifecycleManager lifecycleManager) {
        this.workerProperties = workerProperties;
        this.restTemplate = restTemplate;
        this.lifecycleManager = lifecycleManager;
    }

    /**
     * Loads all active collections from the control plane when the application is ready.
     *
     * <p>Includes retry logic to handle control plane startup timing issues.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Worker '{}' starting bootstrap from control plane at {}",
                workerProperties.getId(), workerProperties.getControlPlaneUrl());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Bootstrap attempt {} of {}", attempt, MAX_RETRIES);

                initializeAllCollections();

                log.info("Worker '{}' successfully bootstrapped all collections",
                        workerProperties.getId());
                return;

            } catch (Exception e) {
                log.warn("Bootstrap attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    log.info("Retrying in {}ms...", RETRY_DELAY_MS);
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Bootstrap retry interrupted");
                        return;
                    }
                } else {
                    log.error("Failed to bootstrap from control plane after {} attempts. " +
                            "Worker will start with no collections loaded.", MAX_RETRIES, e);
                }
            }
        }
    }

    /**
     * Fetches all active collections from the control plane bootstrap endpoint
     * and initializes each one on this worker.
     */
    @SuppressWarnings("unchecked")
    private void initializeAllCollections() {
        String url = workerProperties.getControlPlaneUrl() + "/control/bootstrap";
        log.info("Fetching all active collections from: {}", url);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("Failed to fetch bootstrap config: status={}", response.getStatusCode());
            return;
        }

        Object collectionsObj = response.getBody().get("collections");
        if (!(collectionsObj instanceof List<?> collectionsList)) {
            log.warn("No collections found in bootstrap response");
            return;
        }

        log.info("Found {} collections to initialize", collectionsList.size());

        for (Object item : collectionsList) {
            if (item instanceof Map<?, ?> collectionMap) {
                String collectionId = (String) collectionMap.get("id");
                String collectionName = (String) collectionMap.get("name");
                if (collectionId != null) {
                    try {
                        lifecycleManager.initializeCollection(collectionId);
                    } catch (Exception e) {
                        log.warn("Failed to initialize collection '{}' (id={}): {}",
                                collectionName, collectionId, e.getMessage());
                    }
                }
            }
        }
    }
}
