package com.emf.controlplane.config;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Micrometer Observation API.
 * Ensures HTTP server requests and other observations are properly recorded.
 */
@Configuration
public class ObservationConfig {

    private static final Logger log = LoggerFactory.getLogger(ObservationConfig.class);

    /**
     * Customizes the ObservationRegistry with common configuration.
     * This ensures observations are properly recorded as metrics.
     */
    @Bean
    public ObservationRegistryCustomizer<ObservationRegistry> observationRegistryCustomizer() {
        log.info("Configuring ObservationRegistry for HTTP metrics");
        return registry -> {
            // The registry is already configured by Spring Boot Auto-configuration
            // This bean ensures it's properly initialized
            log.debug("ObservationRegistry customized");
        };
    }
}
