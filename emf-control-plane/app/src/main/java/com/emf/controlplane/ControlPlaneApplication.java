package com.emf.controlplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main entry point for the EMF Control Plane Service.
 * 
 * The Control Plane Service is the central configuration management service for the EMF platform.
 * It provides REST APIs for managing collection definitions, authorization policies, UI configuration,
 * OIDC providers, packages, and schema migrations.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
public class ControlPlaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}
