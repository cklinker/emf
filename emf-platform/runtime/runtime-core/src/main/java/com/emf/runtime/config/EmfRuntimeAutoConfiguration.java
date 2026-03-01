package com.emf.runtime.config;

import com.emf.runtime.events.EventPublisher;
import com.emf.runtime.events.KafkaEventPublisher;
import com.emf.runtime.events.NoOpEventPublisher;
import com.emf.runtime.events.RecordEventPublisher;
import com.emf.runtime.query.DefaultQueryEngine;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.registry.ConcurrentCollectionRegistry;
import com.emf.runtime.router.DynamicCollectionRouter;
import com.emf.runtime.router.GlobalExceptionHandler;
import com.emf.runtime.storage.JsonbStorageAdapter;
import com.emf.runtime.storage.PhysicalTableStorageAdapter;
import com.emf.runtime.storage.SchemaMigrationEngine;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.CustomValidationRuleEngine;
import com.emf.runtime.validation.DefaultValidationEngine;
import com.emf.runtime.validation.ValidationEngine;
import com.emf.runtime.validation.ValidationRuleEvaluator;
import com.emf.runtime.validation.ValidationRuleRegistry;
import com.emf.runtime.workflow.BeforeSaveHookRegistry;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring Boot auto-configuration for EMF Runtime.
 * 
 * <p>Provides default bean configurations for all EMF runtime components:
 * <ul>
 *   <li>CollectionRegistry - ConcurrentCollectionRegistry</li>
 *   <li>StorageAdapter - PhysicalTableStorageAdapter (Mode A) or JsonbStorageAdapter (Mode B)</li>
 *   <li>ValidationEngine - DefaultValidationEngine</li>
 *   <li>QueryEngine - DefaultQueryEngine</li>
 *   <li>EventPublisher - KafkaEventPublisher or NoOpEventPublisher</li>
 *   <li>DynamicCollectionRouter - REST controller</li>
 *   <li>GlobalExceptionHandler - Exception handling</li>
 * </ul>
 * 
 * <p>Storage mode is controlled by the {@code emf.storage.mode} property:
 * <ul>
 *   <li>PHYSICAL_TABLES (default) - Each collection gets its own database table</li>
 *   <li>JSONB_STORE - All collections share a single table with JSONB storage</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(EmfRuntimeProperties.class)
@ComponentScan(basePackages = "com.emf.runtime")
public class EmfRuntimeAutoConfiguration {
    
    /**
     * Creates the collection registry bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public CollectionRegistry collectionRegistry() {
        return new ConcurrentCollectionRegistry();
    }
    
    /**
     * Creates the schema migration engine bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public SchemaMigrationEngine schemaMigrationEngine(JdbcTemplate jdbcTemplate) {
        return new SchemaMigrationEngine(jdbcTemplate);
    }
    
    /**
     * Creates the Physical Table storage adapter (Mode A).
     * This is the default storage mode.
     */
    @Bean
    @ConditionalOnMissingBean(StorageAdapter.class)
    @ConditionalOnProperty(name = "emf.storage.mode", havingValue = "PHYSICAL_TABLES", matchIfMissing = true)
    public StorageAdapter physicalTableStorageAdapter(
            JdbcTemplate jdbcTemplate,
            SchemaMigrationEngine migrationEngine) {
        return new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine);
    }
    
    /**
     * Creates the JSONB storage adapter (Mode B).
     */
    @Bean
    @ConditionalOnMissingBean(StorageAdapter.class)
    @ConditionalOnProperty(name = "emf.storage.mode", havingValue = "JSONB_STORE")
    public StorageAdapter jsonbStorageAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JsonbStorageAdapter(jdbcTemplate, objectMapper);
    }
    
    /**
     * Creates the validation engine bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationEngine validationEngine(StorageAdapter storageAdapter, CollectionRegistry registry) {
        return new DefaultValidationEngine(storageAdapter, registry);
    }
    
    /**
     * Creates the validation rule registry bean.
     * Stores validation rule definitions loaded from the control plane.
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationRuleRegistry validationRuleRegistry() {
        return new ValidationRuleRegistry();
    }

    /**
     * Creates the custom validation rule engine bean.
     * Only created when both a ValidationRuleRegistry and ValidationRuleEvaluator are available.
     */
    @Bean
    @ConditionalOnMissingBean
    public CustomValidationRuleEngine customValidationRuleEngine(
            ValidationRuleRegistry validationRuleRegistry,
            @Autowired(required = false) ValidationRuleEvaluator validationRuleEvaluator) {
        if (validationRuleEvaluator == null) {
            return null;
        }
        return new CustomValidationRuleEngine(validationRuleRegistry, validationRuleEvaluator);
    }

    /**
     * Creates the query engine bean.
     * <p>
     * Optionally wires in:
     * <ul>
     *   <li>{@link BeforeSaveHookRegistry} — module-provided lifecycle hooks</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryEngine queryEngine(StorageAdapter storageAdapter,
                                    ValidationEngine validationEngine,
                                    @Autowired(required = false) CustomValidationRuleEngine customValidationRuleEngine,
                                    @Autowired(required = false) RecordEventPublisher recordEventPublisher,
                                    @Autowired(required = false) BeforeSaveHookRegistry beforeSaveHookRegistry) {
        return new DefaultQueryEngine(storageAdapter, validationEngine,
                null, null, null, null, customValidationRuleEngine, recordEventPublisher,
                beforeSaveHookRegistry);
    }

    /**
     * Creates the Kafka event publisher bean.
     * Only created when Kafka is enabled.
     */
    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    @ConditionalOnProperty(name = "emf.events.enabled", havingValue = "true")
    public EventPublisher kafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                               ObjectMapper objectMapper,
                                               EmfRuntimeProperties properties) {
        return new KafkaEventPublisher(kafkaTemplate, objectMapper, properties.getEvents().getTopicPrefix());
    }
    
    /**
     * Creates a no-op event publisher when events are disabled.
     */
    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher noOpEventPublisher() {
        return new NoOpEventPublisher();
    }
    
    /**
     * Creates the global exception handler bean.
     * Only created if no other GlobalExceptionHandler bean exists.
     */
    @Bean("emfRuntimeGlobalExceptionHandler")
    @ConditionalOnMissingBean(name = "globalExceptionHandler")
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * Configures embedded Tomcat to allow square brackets in query strings.
     *
     * <p>JSON:API uses bracket notation for pagination and filtering parameters
     * (e.g. {@code page[size]=20}, {@code filter[name]=value}). By default Tomcat
     * rejects {@code [} and {@code ]} as invalid characters per RFC 7230/3986.
     * This customizer adds them to the relaxed query character set.
     */
    @Bean
    @ConditionalOnMissingBean(name = "emfTomcatCustomizer")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> emfTomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocol) {
                protocol.setRelaxedQueryChars("[]");
            }
        });
    }
}
