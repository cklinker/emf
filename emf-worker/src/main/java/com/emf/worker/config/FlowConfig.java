package com.emf.worker.config;

import com.emf.runtime.flow.*;
import com.emf.runtime.formula.FormulaEvaluator;
import com.emf.runtime.module.core.CoreActionsModule;
import com.emf.runtime.module.integration.IntegrationModule;
import com.emf.runtime.module.schema.SchemaLifecycleModule;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.service.RollupSummaryService;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.emf.runtime.workflow.BeforeSaveHookRegistry;
import com.emf.runtime.workflow.module.EmfModule;
import com.emf.runtime.workflow.module.ModuleContext;
import com.emf.runtime.workflow.module.ModuleRegistry;
import com.emf.worker.flow.JdbcFlowStore;
import com.emf.worker.listener.CollectionConfigEventPublisher;
import com.emf.worker.listener.FieldConfigEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

/**
 * Spring configuration for the flow execution engine and module system.
 * <p>
 * Wires the {@link FlowEngine} with its dependencies, provides the
 * {@link ActionHandlerRegistry} and {@link BeforeSaveHookRegistry} shared
 * by the flow engine and compile-time modules, and initializes all
 * discovered {@link EmfModule} beans.
 * <p>
 * Enabled by default. Disable with {@code emf.flow.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "emf.flow.enabled", havingValue = "true", matchIfMissing = true)
public class FlowConfig {

    private static final Logger log = LoggerFactory.getLogger(FlowConfig.class);

    // ---------------------------------------------------------------------------
    // Core registries — shared by flows, modules, and before-save hooks
    // ---------------------------------------------------------------------------

    @Bean
    public ActionHandlerRegistry actionHandlerRegistry() {
        return new ActionHandlerRegistry();
    }

    @Bean
    public BeforeSaveHookRegistry beforeSaveHookRegistry() {
        return new BeforeSaveHookRegistry();
    }

    // ---------------------------------------------------------------------------
    // Flow engine beans
    // ---------------------------------------------------------------------------

    @Bean
    public FlowStore flowStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcFlowStore(jdbcTemplate, objectMapper);
    }

    @Bean
    public FlowEngine flowEngine(FlowStore flowStore,
                                  ActionHandlerRegistry actionHandlerRegistry,
                                  ObjectMapper objectMapper,
                                  FlowMetricsConfig flowMetricsConfig,
                                  @Value("${emf.flow.executor.pool-size:10}") int poolSize) {
        log.info("Creating FlowEngine with thread pool size {}", poolSize);
        return new FlowEngine(flowStore, actionHandlerRegistry, objectMapper, poolSize, flowMetricsConfig);
    }

    @Bean
    public FlowTriggerEvaluator flowTriggerEvaluator(FormulaEvaluator formulaEvaluator) {
        return new FlowTriggerEvaluator(formulaEvaluator);
    }

    @Bean
    public InitialStateBuilder initialStateBuilder() {
        return new InitialStateBuilder();
    }

    @Bean
    public WorkflowRuleToFlowMigrator workflowRuleToFlowMigrator() {
        return new WorkflowRuleToFlowMigrator();
    }

    // ---------------------------------------------------------------------------
    // EMF compile-time module beans — registered as Spring beans so that the
    // ModuleRegistry can discover them. These modules live in runtime-module-*
    // JARs outside the worker's component-scan package.
    // ---------------------------------------------------------------------------

    @Bean
    public SchemaLifecycleModule schemaLifecycleModule(JdbcTemplate jdbcTemplate) {
        log.info("Schema-per-tenant active — tenant creation will auto-create PostgreSQL schemas");
        return new SchemaLifecycleModule(slug -> {
            String safeName = slug.replaceAll("[^a-z0-9_-]", "");
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + safeName + "\"");
        });
    }

    @Bean
    public CoreActionsModule coreActionsModule() {
        return new CoreActionsModule();
    }

    @Bean
    public IntegrationModule integrationModule() {
        return new IntegrationModule();
    }

    @Bean
    public ModuleRegistry moduleRegistry(ActionHandlerRegistry actionHandlerRegistry,
                                          BeforeSaveHookRegistry beforeSaveHookRegistry,
                                          @Autowired(required = false) List<EmfModule> discoveredModules,
                                          QueryEngine queryEngine,
                                          CollectionRegistry collectionRegistry,
                                          @Autowired(required = false) FormulaEvaluator formulaEvaluator,
                                          ObjectMapper objectMapper,
                                          FlowEngine flowEngine,
                                          RollupSummaryService rollupSummaryService) {
        ModuleRegistry registry = new ModuleRegistry(actionHandlerRegistry, beforeSaveHookRegistry);

        if (discoveredModules != null && !discoveredModules.isEmpty()) {
            ModuleContext context = new ModuleContext(
                queryEngine, collectionRegistry, formulaEvaluator, objectMapper,
                actionHandlerRegistry, Map.of(
                    FlowEngine.class, flowEngine,
                    RollupSummaryService.class, rollupSummaryService));

            registry.initialize(discoveredModules, context);
            log.info("Module system initialized with {} modules", discoveredModules.size());
        } else {
            log.info("No EmfModule beans found — module system inactive");
        }

        return registry;
    }

    // ---------------------------------------------------------------------------
    // Before-save hook publishers — emit Kafka events on collection/field changes
    // ---------------------------------------------------------------------------

    @Bean
    public CollectionConfigEventPublisher collectionConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        CollectionConfigEventPublisher publisher =
                new CollectionConfigEventPublisher(kafkaTemplate, objectMapper);
        hookRegistry.register(publisher);
        return publisher;
    }

    @Bean
    public FieldConfigEventPublisher fieldConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        FieldConfigEventPublisher publisher =
                new FieldConfigEventPublisher(kafkaTemplate, objectMapper);
        hookRegistry.register(publisher);
        return publisher;
    }
}
