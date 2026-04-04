package io.kelta.worker.config;

import io.kelta.runtime.flow.*;
import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.module.core.CoreActionsModule;
import io.kelta.runtime.module.integration.IntegrationModule;
import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.graalvm.GraalVmScriptExecutor;
import io.kelta.runtime.module.schema.SchemaLifecycleModule;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.service.RollupSummaryService;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.runtime.workflow.module.KeltaModule;
import io.kelta.runtime.workflow.module.ModuleContext;
import io.kelta.runtime.workflow.module.ModuleRegistry;
import io.kelta.worker.flow.JdbcFlowStore;
import io.kelta.worker.listener.CollectionConfigEventPublisher;
import io.kelta.worker.listener.FieldConfigEventPublisher;
import io.kelta.worker.listener.ApprovalProcessConfigHook;
import io.kelta.worker.listener.ApprovalRecordLockHook;
import io.kelta.worker.listener.CerbosPolicySyncHook;
import io.kelta.worker.listener.ValidationRuleRefreshHook;
import io.kelta.worker.handler.SubmitForApprovalActionHandler;
import io.kelta.worker.repository.ApprovalRepository;
import io.kelta.worker.service.ApprovalService;
import io.kelta.worker.service.AuditBeforeSaveHook;
import io.kelta.worker.service.CerbosPolicySyncService;
import io.kelta.worker.service.SetupAuditService;
import tools.jackson.databind.ObjectMapper;
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
 * discovered {@link KeltaModule} beans.
 * <p>
 * Enabled by default. Disable with {@code kelta.flow.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "kelta.flow.enabled", havingValue = "true", matchIfMissing = true)
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
                                  @Value("${kelta.flow.executor.pool-size:10}") int poolSize) {
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
    // Kelta compile-time module beans — registered as Spring beans so that the
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
    public ScriptExecutor scriptExecutor(
            @Value("${kelta.script.timeout-seconds:30}") int timeoutSeconds) {
        return new GraalVmScriptExecutor(timeoutSeconds);
    }

    @Bean
    public ModuleRegistry moduleRegistry(ActionHandlerRegistry actionHandlerRegistry,
                                          BeforeSaveHookRegistry beforeSaveHookRegistry,
                                          @Autowired(required = false) List<KeltaModule> discoveredModules,
                                          QueryEngine queryEngine,
                                          CollectionRegistry collectionRegistry,
                                          @Autowired(required = false) FormulaEvaluator formulaEvaluator,
                                          ObjectMapper objectMapper,
                                          FlowEngine flowEngine,
                                          RollupSummaryService rollupSummaryService,
                                          @Autowired(required = false) EmailService emailService,
                                          @Autowired(required = false) ScriptExecutor scriptExecutor) {
        ModuleRegistry registry = new ModuleRegistry(actionHandlerRegistry, beforeSaveHookRegistry);

        if (discoveredModules != null && !discoveredModules.isEmpty()) {
            var extensions = new java.util.HashMap<>(Map.of(
                    FlowEngine.class, (Object) flowEngine,
                    RollupSummaryService.class, (Object) rollupSummaryService));

            if (emailService != null) {
                extensions.put(EmailService.class, emailService);
                log.info("EmailService wired into module context — flow email alerts will use real delivery");
            }

            if (scriptExecutor != null) {
                extensions.put(ScriptExecutor.class, scriptExecutor);
                log.info("ScriptExecutor wired into module context — flow scripts will use GraalVM execution");
            }

            ModuleContext context = new ModuleContext(
                queryEngine, collectionRegistry, formulaEvaluator, objectMapper,
                actionHandlerRegistry, extensions);

            registry.initialize(discoveredModules, context);
            log.info("Module system initialized with {} modules", discoveredModules.size());
        } else {
            log.info("No KeltaModule beans found — module system inactive");
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

    // ---------------------------------------------------------------------------
    // Audit hook — records setup changes to the setup_audit_trail table
    // ---------------------------------------------------------------------------

    @Bean
    public CerbosPolicySyncHook cerbosPolicySyncHook(
            BeforeSaveHookRegistry hookRegistry,
            CerbosPolicySyncService syncService) {
        CerbosPolicySyncHook hook = new CerbosPolicySyncHook(syncService);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public AuditBeforeSaveHook auditBeforeSaveHook(
            BeforeSaveHookRegistry hookRegistry,
            SetupAuditService auditService) {
        AuditBeforeSaveHook hook = new AuditBeforeSaveHook(auditService);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public ValidationRuleRefreshHook validationRuleRefreshHook(
            BeforeSaveHookRegistry hookRegistry,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        ValidationRuleRefreshHook hook = new ValidationRuleRefreshHook(
                kafkaTemplate, objectMapper, jdbcTemplate);
        hookRegistry.register(hook);
        return hook;
    }

    // ---------------------------------------------------------------------------
    // Approval workflow hooks and handlers
    // ---------------------------------------------------------------------------

    @Bean
    public ApprovalProcessConfigHook approvalProcessConfigHook(
            BeforeSaveHookRegistry hookRegistry,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        ApprovalProcessConfigHook hook = new ApprovalProcessConfigHook(kafkaTemplate, objectMapper);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public ApprovalRecordLockHook approvalRecordLockHook(
            BeforeSaveHookRegistry hookRegistry,
            ApprovalRepository approvalRepository,
            CollectionRegistry collectionRegistry) {
        ApprovalRecordLockHook hook = new ApprovalRecordLockHook(
                approvalRepository, collectionRegistry);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public SubmitForApprovalActionHandler submitForApprovalActionHandler(
            ActionHandlerRegistry actionHandlerRegistry,
            ApprovalService approvalService,
            ObjectMapper objectMapper) {
        SubmitForApprovalActionHandler handler =
                new SubmitForApprovalActionHandler(approvalService, objectMapper);
        actionHandlerRegistry.register(handler);
        return handler;
    }
}
