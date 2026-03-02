package com.emf.runtime.config;

import com.emf.runtime.events.RecordEventPublisher;
import com.emf.runtime.query.DefaultQueryEngine;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.registry.ConcurrentCollectionRegistry;
import com.emf.runtime.router.DynamicCollectionRouter;
import com.emf.runtime.router.GlobalExceptionHandler;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring Boot auto-configuration for EMF Runtime.
 * 
 * <p>Provides default bean configurations for all EMF runtime components:
 * <ul>
 *   <li>CollectionRegistry - ConcurrentCollectionRegistry</li>
 *   <li>StorageAdapter - PhysicalTableStorageAdapter</li>
 *   <li>ValidationEngine - DefaultValidationEngine</li>
 *   <li>QueryEngine - DefaultQueryEngine</li>
 *   <li>DynamicCollectionRouter - REST controller</li>
 *   <li>GlobalExceptionHandler - Exception handling</li>
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
     * Creates the physical table storage adapter.
     */
    @Bean
    @ConditionalOnMissingBean(StorageAdapter.class)
    public StorageAdapter physicalTableStorageAdapter(
            JdbcTemplate jdbcTemplate,
            SchemaMigrationEngine migrationEngine,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine, objectMapper);
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
