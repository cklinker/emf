package com.emf.worker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for storage and HTTP client beans used by the worker service.
 *
 * <p>The core storage beans (DataSource, JdbcTemplate, StorageAdapter, CollectionRegistry,
 * QueryEngine, ValidationEngine, DynamicCollectionRouter) are auto-configured by
 * runtime-core's {@code EmfRuntimeAutoConfiguration} via Spring Boot auto-configuration.
 *
 * <p>This configuration provides additional beans needed by the worker service.
 */
@Configuration
public class StorageConfig {

    /**
     * Creates a RestTemplate for HTTP calls to the control plane.
     *
     * @return a new RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
