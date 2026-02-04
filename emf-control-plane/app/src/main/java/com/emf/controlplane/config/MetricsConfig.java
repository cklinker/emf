package com.emf.controlplane.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Micrometer metrics with common tags and custom business metrics.
 * 
 * This configuration:
 * - Adds common tags to all metrics (application name, environment)
 * - Enables @Timed annotation support for method-level timing
 * - Provides factory methods for creating custom business metrics
 * 
 * Requirements satisfied:
 * - 13.3: Expose Prometheus metrics via /actuator/metrics endpoint
 * - 13.4: Emit OpenTelemetry traces for all requests (via Micrometer Tracing bridge)
 */
@Configuration
public class MetricsConfig {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfig.class);

    private final ControlPlaneProperties properties;

    public MetricsConfig(ControlPlaneProperties properties) {
        this.properties = properties;
    }

    /**
     * Customizes the MeterRegistry with common tags applied to all metrics.
     * These tags help identify metrics in multi-service environments.
     * 
     * @return MeterRegistryCustomizer that adds common tags
     * 
     * Validates: Requirement 13.3
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        log.info("Configuring metrics with common tags");
        
        return registry -> registry.config()
                .commonTags(
                        "application", "control-plane-service",
                        "service", "emf-control-plane"
                );
    }

    /**
     * Enables the @Timed annotation for method-level timing metrics.
     * This allows services to easily add timing metrics to methods.
     * 
     * @param registry The MeterRegistry to use
     * @return TimedAspect for @Timed annotation support
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        log.info("Enabling @Timed annotation support");
        return new TimedAspect(registry);
    }

    /**
     * Factory for creating business operation counters.
     * Use this to track counts of specific business operations.
     */
    @Bean
    public BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new BusinessMetrics(registry);
    }

    /**
     * Holder class for custom business metrics.
     * Provides pre-configured counters and timers for common operations.
     */
    public static class BusinessMetrics {

        private final MeterRegistry registry;

        // Collection operation counters
        private final Counter collectionsCreated;
        private final Counter collectionsUpdated;
        private final Counter collectionsDeleted;

        // Field operation counters
        private final Counter fieldsAdded;
        private final Counter fieldsUpdated;
        private final Counter fieldsDeleted;

        // Authorization operation counters
        private final Counter rolesCreated;
        private final Counter policiesCreated;

        // Event publishing counters
        private final Counter eventsPublished;
        private final Counter eventsPublishFailed;

        // Cache operation counters
        private final Counter cacheHits;
        private final Counter cacheMisses;

        // JWKS operation counters
        private final Counter jwksFetched;
        private final Counter jwksFetchFailed;

        // Timers for operation latency
        private final Timer collectionOperationTimer;
        private final Timer eventPublishTimer;
        private final Timer jwksFetchTimer;

        public BusinessMetrics(MeterRegistry registry) {
            this.registry = registry;

            // Collection metrics
            this.collectionsCreated = Counter.builder("emf.collections.created")
                    .description("Number of collections created")
                    .register(registry);
            this.collectionsUpdated = Counter.builder("emf.collections.updated")
                    .description("Number of collections updated")
                    .register(registry);
            this.collectionsDeleted = Counter.builder("emf.collections.deleted")
                    .description("Number of collections deleted")
                    .register(registry);

            // Field metrics
            this.fieldsAdded = Counter.builder("emf.fields.added")
                    .description("Number of fields added to collections")
                    .register(registry);
            this.fieldsUpdated = Counter.builder("emf.fields.updated")
                    .description("Number of fields updated")
                    .register(registry);
            this.fieldsDeleted = Counter.builder("emf.fields.deleted")
                    .description("Number of fields deleted")
                    .register(registry);

            // Authorization metrics
            this.rolesCreated = Counter.builder("emf.roles.created")
                    .description("Number of roles created")
                    .register(registry);
            this.policiesCreated = Counter.builder("emf.policies.created")
                    .description("Number of policies created")
                    .register(registry);

            // Event metrics
            this.eventsPublished = Counter.builder("emf.events.published")
                    .description("Number of events published to Kafka")
                    .register(registry);
            this.eventsPublishFailed = Counter.builder("emf.events.publish.failed")
                    .description("Number of failed event publish attempts")
                    .register(registry);

            // Cache metrics
            this.cacheHits = Counter.builder("emf.cache.hits")
                    .description("Number of cache hits")
                    .register(registry);
            this.cacheMisses = Counter.builder("emf.cache.misses")
                    .description("Number of cache misses")
                    .register(registry);

            // JWKS metrics
            this.jwksFetched = Counter.builder("emf.jwks.fetched")
                    .description("Number of successful JWKS fetches")
                    .register(registry);
            this.jwksFetchFailed = Counter.builder("emf.jwks.fetch.failed")
                    .description("Number of failed JWKS fetch attempts")
                    .register(registry);

            // Timers
            this.collectionOperationTimer = Timer.builder("emf.collections.operation.duration")
                    .description("Time taken for collection operations")
                    .register(registry);
            this.eventPublishTimer = Timer.builder("emf.events.publish.duration")
                    .description("Time taken to publish events")
                    .register(registry);
            this.jwksFetchTimer = Timer.builder("emf.jwks.fetch.duration")
                    .description("Time taken to fetch JWKS")
                    .register(registry);
        }

        // Collection operation methods
        public void recordCollectionCreated() {
            collectionsCreated.increment();
        }

        public void recordCollectionUpdated() {
            collectionsUpdated.increment();
        }

        public void recordCollectionDeleted() {
            collectionsDeleted.increment();
        }

        // Field operation methods
        public void recordFieldAdded() {
            fieldsAdded.increment();
        }

        public void recordFieldUpdated() {
            fieldsUpdated.increment();
        }

        public void recordFieldDeleted() {
            fieldsDeleted.increment();
        }

        // Authorization operation methods
        public void recordRoleCreated() {
            rolesCreated.increment();
        }

        public void recordPolicyCreated() {
            policiesCreated.increment();
        }

        // Event operation methods
        public void recordEventPublished() {
            eventsPublished.increment();
        }

        public void recordEventPublishFailed() {
            eventsPublishFailed.increment();
        }

        // Cache operation methods
        public void recordCacheHit() {
            cacheHits.increment();
        }

        public void recordCacheMiss() {
            cacheMisses.increment();
        }

        // JWKS operation methods
        public void recordJwksFetched() {
            jwksFetched.increment();
        }

        public void recordJwksFetchFailed() {
            jwksFetchFailed.increment();
        }

        // Timer access methods
        public Timer getCollectionOperationTimer() {
            return collectionOperationTimer;
        }

        public Timer getEventPublishTimer() {
            return eventPublishTimer;
        }

        public Timer getJwksFetchTimer() {
            return jwksFetchTimer;
        }

        /**
         * Creates a counter with custom tags for specific operation types.
         * 
         * @param name The metric name
         * @param description The metric description
         * @param tags Additional tags as key-value pairs
         * @return A new Counter
         */
        public Counter createCounter(String name, String description, String... tags) {
            return Counter.builder(name)
                    .description(description)
                    .tags(tags)
                    .register(registry);
        }

        /**
         * Creates a timer with custom tags for specific operation types.
         * 
         * @param name The metric name
         * @param description The metric description
         * @param tags Additional tags as key-value pairs
         * @return A new Timer
         */
        public Timer createTimer(String name, String description, String... tags) {
            return Timer.builder(name)
                    .description(description)
                    .tags(tags)
                    .register(registry);
        }
    }
}
