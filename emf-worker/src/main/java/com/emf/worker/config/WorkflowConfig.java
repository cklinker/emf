package com.emf.worker.config;

import com.emf.runtime.formula.FormulaEvaluator;
import com.emf.runtime.module.core.CoreActionsModule;
import com.emf.runtime.module.integration.IntegrationModule;
import com.emf.runtime.module.schema.SchemaLifecycleModule;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.workflow.*;
import com.emf.runtime.workflow.module.EmfModule;
import com.emf.runtime.workflow.module.ModuleContext;
import com.emf.runtime.workflow.module.ModuleRegistry;
import com.emf.worker.listener.CollectionConfigEventPublisher;
import com.emf.worker.listener.FieldConfigEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

/**
 * Spring configuration for the worker's embedded workflow engine.
 * <p>
 * Wires the {@link WorkflowEngine} with:
 * <ul>
 *   <li>{@link JdbcWorkflowStore} for direct database access to workflow tables</li>
 *   <li>{@link ActionHandlerRegistry} populated by the module system</li>
 *   <li>{@link BeforeSaveHookRegistry} populated by the module system</li>
 *   <li>{@link FormulaEvaluator} for filter formula evaluation</li>
 * </ul>
 * <p>
 * Enabled by default. Disable with {@code emf.workflow.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "emf.workflow.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfig.class);

    @Bean
    public ActionHandlerRegistry actionHandlerRegistry() {
        return new ActionHandlerRegistry();
    }

    @Bean
    public BeforeSaveHookRegistry beforeSaveHookRegistry() {
        return new BeforeSaveHookRegistry();
    }

    // ---------------------------------------------------------------------------
    // EMF module beans — must be registered as Spring beans so that the
    // ModuleRegistry can discover them via @Autowired List<EmfModule>.
    // These modules live in the runtime-module-* JARs which are outside the
    // worker's @SpringBootApplication component-scan package.
    // ---------------------------------------------------------------------------

    @Bean
    public SchemaLifecycleModule schemaLifecycleModule() {
        return new SchemaLifecycleModule();
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
                                          WorkflowEngine workflowEngine) {
        ModuleRegistry registry = new ModuleRegistry(actionHandlerRegistry, beforeSaveHookRegistry);

        if (discoveredModules != null && !discoveredModules.isEmpty()) {
            // Pass WorkflowEngine via extensions so modules (e.g., TriggerFlowActionHandler) can use it
            ModuleContext context = new ModuleContext(
                queryEngine, collectionRegistry, formulaEvaluator, objectMapper,
                actionHandlerRegistry, Map.of(WorkflowEngine.class, workflowEngine));

            registry.initialize(discoveredModules, context);
            log.info("Module system initialized with {} modules", discoveredModules.size());
        } else {
            log.info("No EmfModule beans found — module system inactive");
        }

        return registry;
    }

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

    @Bean
    public WorkflowStore workflowStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcWorkflowStore(jdbcTemplate, objectMapper);
    }

    @Bean
    public WorkflowEngine workflowEngine(WorkflowStore workflowStore,
                                           ActionHandlerRegistry actionHandlerRegistry,
                                           FormulaEvaluator formulaEvaluator,
                                           ObjectMapper objectMapper) {
        return new WorkflowEngine(workflowStore, actionHandlerRegistry,
            formulaEvaluator, objectMapper);
    }
}
