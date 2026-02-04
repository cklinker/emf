package com.emf.controlplane.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenTelemetry tracing via Micrometer Tracing bridge.
 * 
 * This configuration:
 * - Enables @Observed annotation support for method-level tracing
 * - Configures the observation registry for distributed tracing
 * - Integrates with OpenTelemetry OTLP exporter
 * 
 * The actual OpenTelemetry configuration is handled by Spring Boot auto-configuration
 * based on the dependencies (micrometer-tracing-bridge-otel, opentelemetry-exporter-otlp)
 * and application.yml settings.
 * 
 * Requirements satisfied:
 * - 13.4: Emit OpenTelemetry traces for all requests
 */
@Configuration
@ConditionalOnClass(ObservationRegistry.class)
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    /**
     * Enables the @Observed annotation for method-level observation/tracing.
     * This allows services to easily add tracing spans to methods.
     * 
     * @param observationRegistry The ObservationRegistry to use
     * @return ObservedAspect for @Observed annotation support
     * 
     * Validates: Requirement 13.4
     */
    @Bean
    @ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("Enabling @Observed annotation support for distributed tracing");
        return new ObservedAspect(observationRegistry);
    }
}
