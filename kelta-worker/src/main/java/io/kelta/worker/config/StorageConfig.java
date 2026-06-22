package io.kelta.worker.config;

import io.kelta.runtime.registry.CollectionOnDemandLoader;
import io.kelta.runtime.storage.CredentialProvider;
import io.kelta.runtime.storage.ExternalJdbcConnectionProvider;
import io.kelta.runtime.storage.ExternalJdbcStorageAdapter;
import io.kelta.runtime.storage.ExternalRestStorageAdapter;
import io.kelta.runtime.storage.RestExecutor;
import io.kelta.worker.service.CollectionLifecycleManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

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

    /**
     * Registers the external REST storage adapter so the {@code DispatchingStorageAdapter}
     * routes {@code adapterType=external-rest} collections to it (Rec 4 slice 4d-2b).
     */
    @Bean
    public ExternalRestStorageAdapter externalRestStorageAdapter(
            RestExecutor restExecutor, ObjectMapper objectMapper, CredentialProvider credentialProvider) {
        return new ExternalRestStorageAdapter(restExecutor, objectMapper, credentialProvider);
    }

    /**
     * Registers the external JDBC storage adapter so the {@code DispatchingStorageAdapter}
     * routes {@code adapterType=external-jdbc} collections to it (Rec 4 slice 4d-2b).
     */
    @Bean
    public ExternalJdbcStorageAdapter externalJdbcStorageAdapter(
            ExternalJdbcConnectionProvider connectionProvider, CredentialProvider credentialProvider) {
        return new ExternalJdbcStorageAdapter(connectionProvider, credentialProvider);
    }
}
