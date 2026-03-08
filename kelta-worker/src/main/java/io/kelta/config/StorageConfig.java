package io.kelta.worker.config;

import io.kelta.runtime.registry.CollectionOnDemandLoader;
import io.kelta.worker.service.CollectionLifecycleManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for storage beans used by the worker service.
 *
 * <p>The core storage beans (DataSource, JdbcTemplate, StorageAdapter, CollectionRegistry,
 * QueryEngine, ValidationEngine, DynamicCollectionRouter) are auto-configured by
 * runtime-core's {@code KeltaRuntimeAutoConfiguration} via Spring Boot auto-configuration.
 *
 * <p>This configuration provides additional beans needed by the worker service.
 */
@Configuration
public class StorageConfig {

    /**
     * Creates the on-demand collection loader that fetches unknown collections
     * from the database when a request arrives for a collection not yet
     * loaded by this worker.
     *
     * @param lifecycleManager the collection lifecycle manager
     * @return the on-demand loader
     */
    @Bean
    public CollectionOnDemandLoader collectionOnDemandLoader(CollectionLifecycleManager lifecycleManager) {
        return lifecycleManager::loadCollectionByName;
    }
}
