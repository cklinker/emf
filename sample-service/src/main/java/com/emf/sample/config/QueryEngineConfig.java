package com.emf.sample.config;

import com.emf.runtime.query.DefaultQueryEngine;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.ValidationEngine;
import com.emf.sample.service.EventPublishingQueryEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for query engine with event publishing.
 */
@Configuration
public class QueryEngineConfig {
    
    /**
     * Creates the base query engine (not primary).
     */
    @Bean(name = "baseQueryEngine")
    @ConditionalOnMissingBean(name = "baseQueryEngine")
    public QueryEngine baseQueryEngine(StorageAdapter storageAdapter, ValidationEngine validationEngine) {
        return new DefaultQueryEngine(storageAdapter, validationEngine);
    }
    
    /**
     * Creates the event-publishing query engine wrapper (primary).
     * This will be injected wherever QueryEngine is needed.
     */
    @Bean
    @Primary
    public QueryEngine eventPublishingQueryEngine(QueryEngine baseQueryEngine, 
                                                    ApplicationEventPublisher eventPublisher) {
        return new EventPublishingQueryEngine(baseQueryEngine, eventPublisher);
    }
}
