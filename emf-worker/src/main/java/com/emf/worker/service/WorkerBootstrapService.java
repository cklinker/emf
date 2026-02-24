package com.emf.worker.service;

import com.emf.worker.config.WorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Bootstraps the worker by loading all active collections from the database on startup.
 *
 * <p>On {@link ApplicationReadyEvent}, queries the database directly for all active
 * collections and initializes each one via {@link CollectionLifecycleManager}.
 * This ensures every worker can serve every collection, allowing the K8s Service
 * to load-balance requests across all worker pods.
 *
 * <p>There is no control plane dependency. The worker reads collection definitions
 * directly from the shared database. Runtime schema changes are handled by the
 * existing Kafka {@code collection-changed} listener.
 *
 * @since 1.0.0
 */
@Component
public class WorkerBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(WorkerBootstrapService.class);

    private static final String SELECT_ACTIVE_COLLECTIONS =
            "SELECT id, name FROM collection WHERE active = true";

    private final WorkerProperties workerProperties;
    private final JdbcTemplate jdbcTemplate;
    private final CollectionLifecycleManager lifecycleManager;

    public WorkerBootstrapService(WorkerProperties workerProperties,
                                   JdbcTemplate jdbcTemplate,
                                   CollectionLifecycleManager lifecycleManager) {
        this.workerProperties = workerProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.lifecycleManager = lifecycleManager;
    }

    /**
     * Loads all active collections from the database when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Worker '{}' starting bootstrap from database", workerProperties.getId());

        try {
            initializeAllCollections();
            log.info("Worker '{}' successfully bootstrapped all collections",
                    workerProperties.getId());
        } catch (Exception e) {
            log.error("Failed to bootstrap collections from database. " +
                    "Worker will start with no collections loaded.", e);
        }
    }

    /**
     * Queries all active collections from the database and initializes each one
     * on this worker.
     */
    private void initializeAllCollections() {
        List<Map<String, Object>> collections = jdbcTemplate.queryForList(SELECT_ACTIVE_COLLECTIONS);

        log.info("Found {} active collections to initialize", collections.size());

        for (Map<String, Object> collection : collections) {
            String collectionId = (String) collection.get("id");
            String collectionName = (String) collection.get("name");

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
