package com.emf.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the EMF Worker Service.
 *
 * <p>The worker service is a generic collection hosting service that:
 * <ul>
 *   <li>Registers itself with the control plane on startup</li>
 *   <li>Receives collection assignments via Kafka events</li>
 *   <li>Initializes storage and REST endpoints for assigned collections</li>
 *   <li>Sends periodic heartbeats to the control plane</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
