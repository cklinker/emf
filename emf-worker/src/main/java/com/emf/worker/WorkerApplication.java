package com.emf.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the EMF Worker Service.
 *
 * <p>The worker service is a generic collection hosting service that:
 * <ul>
 *   <li>Loads all active collections from the control plane on startup</li>
 *   <li>Listens for collection schema changes via Kafka events</li>
 *   <li>Provides REST endpoints for all collections via DynamicCollectionRouter</li>
 *   <li>Executes workflow rules (after-save via Kafka, before-save in-process, scheduled via poll)</li>
 * </ul>
 *
 * <p>Pod health is managed by Kubernetes (liveness/readiness probes).
 * All workers serve all collections, enabling K8s Service load balancing.
 */
@SpringBootApplication
@EnableScheduling
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
