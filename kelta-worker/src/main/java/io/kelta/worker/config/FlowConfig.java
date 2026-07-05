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
import io.kelta.worker.listener.FlowConfigEventPublisher;
import io.kelta.worker.listener.FlowDefinitionValidationHook;
import io.kelta.worker.listener.FlowScheduleSyncHook;
import io.kelta.worker.listener.ApprovalProcessConfigHook;
import io.kelta.worker.listener.ApprovalRecordLockHook;
import io.kelta.worker.listener.CerbosPolicySyncHook;
import io.kelta.worker.listener.FieldPermissionSyncHook;
import io.kelta.worker.listener.ApiSpecConfigHook;
import io.kelta.worker.listener.CredentialEncryptionHook;
import io.kelta.worker.listener.CredentialEventPublisher;
import io.kelta.runtime.module.integration.api.OpenApiSpecParser;
import io.kelta.worker.listener.RecordTypeEnforcementHook;
import io.kelta.worker.listener.LayoutFieldRefreshHook;
import io.kelta.worker.listener.LayoutRelatedListRefreshHook;
import io.kelta.worker.listener.LayoutRuleRefreshHook;
import io.kelta.worker.listener.LayoutSectionRefreshHook;
import io.kelta.worker.listener.PageLayoutConfigEventPublisher;
import io.kelta.worker.listener.ValidationRuleRefreshHook;
import io.kelta.crypto.EncryptionService;
import io.kelta.runtime.credential.CredentialTypeRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.kelta.worker.handler.SubmitForApprovalActionHandler;
import io.kelta.worker.repository.ApprovalRepository;
import io.kelta.worker.service.ApprovalService;
import io.kelta.worker.service.AuditBeforeSaveHook;
import io.kelta.worker.service.CerbosPolicySyncCoalescer;
import io.kelta.worker.service.CollectionLifecycleManager;
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
import io.kelta.runtime.event.PlatformEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    /**
     * Tenant slug grammar for schema creation. Must start with a lowercase
     * letter, followed by lowercase letters / digits / hyphens only, and
     * bounded at 63 chars — Postgres's identifier length limit. Mirrors the
     * upstream validation in {@code TenantLifecycleHook.beforeCreate}, so a
     * slug that passed the lifecycle hook will pass here too; this regex is
     * defense-in-depth against any callback wiring that bypasses the hook.
     */
    private static final Pattern TENANT_SLUG_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]{0,62}$");

    @Bean
    public SchemaLifecycleModule schemaLifecycleModule(JdbcTemplate jdbcTemplate) {
        log.info("Schema-per-tenant active — tenant creation will auto-create PostgreSQL schemas");
        return new SchemaLifecycleModule(slug -> {
            if (slug == null || !TENANT_SLUG_PATTERN.matcher(slug).matches()) {
                // Fail loudly rather than silently creating a schema with a
                // truncated / empty / surprising name. The upstream hook already
                // validated the slug before calling us, so hitting this branch
                // means a platform invariant was broken somewhere.
                throw new IllegalArgumentException(
                        "Tenant slug '" + slug + "' does not match required pattern "
                                + TENANT_SLUG_PATTERN.pattern()
                                + " — refusing to CREATE SCHEMA");
            }
            // The regex guarantees the slug contains no double-quotes, but
            // double any defensively before splicing into the DDL string.
            String escaped = slug.replace("\"", "\"\"");
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + escaped + "\"");
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
    public org.springframework.transaction.support.TransactionTemplate flowTransactionTemplate(
            org.springframework.transaction.PlatformTransactionManager txManager) {
        return new org.springframework.transaction.support.TransactionTemplate(txManager);
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
                                          JdbcTemplate jdbcTemplate,
                                          org.springframework.transaction.support.TransactionTemplate transactionTemplate,
                                          @Autowired(required = false) EmailService emailService,
                                          @Autowired(required = false) ScriptExecutor scriptExecutor,
                                          @Autowired(required = false)
                                              io.kelta.runtime.module.integration.spi.ApiSpecStore apiSpecStore,
                                          @Autowired(required = false)
                                              io.kelta.runtime.module.integration.spi.CredentialResolverPort credentialResolverPort,
                                          @Autowired(required = false)
                                              io.kelta.runtime.module.integration.spi.IdempotencyStore idempotencyStore) {
        ModuleRegistry registry = new ModuleRegistry(actionHandlerRegistry, beforeSaveHookRegistry);

        if (discoveredModules != null && !discoveredModules.isEmpty()) {
            var extensions = new java.util.HashMap<Class<?>, Object>();
            extensions.put(FlowEngine.class, flowEngine);
            extensions.put(RollupSummaryService.class, rollupSummaryService);
            extensions.put(JdbcTemplate.class, jdbcTemplate);
            extensions.put(org.springframework.transaction.support.TransactionTemplate.class,
                    transactionTemplate);

            if (emailService != null) {
                extensions.put(EmailService.class, emailService);
                log.info("EmailService wired into module context — flow email alerts will use real delivery");
            }

            if (scriptExecutor != null) {
                extensions.put(ScriptExecutor.class, scriptExecutor);
                log.info("ScriptExecutor wired into module context — flow scripts will use GraalVM execution");
            }

            // PR 4: wire CALL_API SPI implementations so the integration
            // module can resolve specs, credentials, and idempotency state.
            if (apiSpecStore != null) {
                extensions.put(io.kelta.runtime.module.integration.spi.ApiSpecStore.class, apiSpecStore);
                log.info("ApiSpecStore wired into module context — CALL_API operation mode active");
            }
            if (credentialResolverPort != null) {
                extensions.put(io.kelta.runtime.module.integration.spi.CredentialResolverPort.class,
                    credentialResolverPort);
                log.info("CredentialResolverPort wired into module context — CALL_API can attach credentials");
            }
            if (idempotencyStore != null) {
                extensions.put(io.kelta.runtime.module.integration.spi.IdempotencyStore.class,
                    idempotencyStore);
                log.info("IdempotencyStore wired into module context — CALL_API will dedupe non-idempotent calls");
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
    // Before-save hook publishers — emit events on collection/field changes
    // ---------------------------------------------------------------------------

    @Bean
    public CollectionConfigEventPublisher collectionConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        CollectionConfigEventPublisher publisher =
                new CollectionConfigEventPublisher(eventPublisher);
        hookRegistry.register(publisher);
        return publisher;
    }

    @Bean
    public io.kelta.worker.listener.CollectionQuotaEnforcementHook collectionQuotaEnforcementHook(
            BeforeSaveHookRegistry hookRegistry,
            io.kelta.worker.service.TenantQuotaResolver quotaResolver,
            io.kelta.worker.repository.GovernorLimitsRepository governorLimitsRepository) {
        io.kelta.worker.listener.CollectionQuotaEnforcementHook hook =
                new io.kelta.worker.listener.CollectionQuotaEnforcementHook(quotaResolver, governorLimitsRepository);
        hookRegistry.register(hook);
        return hook;
    }

    /**
     * Runs tenant-defined record-event scripts (record-scripts system collection) through the
     * sandboxed GraalVM ScriptExecutor on record writes (unified record experience, slice 7).
     */
    @Bean
    public io.kelta.worker.listener.RecordScriptHook recordScriptHook(
            BeforeSaveHookRegistry hookRegistry,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            ScriptExecutor scriptExecutor) {
        io.kelta.worker.listener.RecordScriptHook hook =
                new io.kelta.worker.listener.RecordScriptHook(jdbcTemplate, scriptExecutor);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.FieldQuotaEnforcementHook fieldQuotaEnforcementHook(
            BeforeSaveHookRegistry hookRegistry,
            io.kelta.worker.service.TenantQuotaResolver quotaResolver,
            JdbcTemplate jdbcTemplate) {
        io.kelta.worker.listener.FieldQuotaEnforcementHook hook =
                new io.kelta.worker.listener.FieldQuotaEnforcementHook(quotaResolver, jdbcTemplate);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.UIPageSlugHook uiPageSlugHook(
            BeforeSaveHookRegistry hookRegistry,
            JdbcTemplate jdbcTemplate) {
        io.kelta.worker.listener.UIPageSlugHook hook =
                new io.kelta.worker.listener.UIPageSlugHook(jdbcTemplate);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public FieldConfigEventPublisher fieldConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher,
            JdbcTemplate jdbcTemplate,
            CollectionLifecycleManager lifecycleManager,
            io.kelta.worker.service.CerbosAuthorizationService cerbosAuthorizationService,
            io.kelta.worker.service.FormulaRecomputeService formulaRecomputeService) {
        FieldConfigEventPublisher publisher =
                new FieldConfigEventPublisher(eventPublisher, jdbcTemplate, lifecycleManager,
                        cerbosAuthorizationService, formulaRecomputeService);
        hookRegistry.register(publisher);
        return publisher;
    }

    @Bean
    public FlowConfigEventPublisher flowConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        FlowConfigEventPublisher publisher =
                new FlowConfigEventPublisher(eventPublisher);
        hookRegistry.register(publisher);
        return publisher;
    }

    @Bean
    public FlowDefinitionValidationHook flowDefinitionValidationHook(
            BeforeSaveHookRegistry hookRegistry,
            ObjectMapper objectMapper) {
        FlowDefinitionValidationHook hook = new FlowDefinitionValidationHook(objectMapper);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public FlowScheduleSyncHook flowScheduleSyncHook(
            BeforeSaveHookRegistry hookRegistry,
            io.kelta.worker.repository.ScheduledJobRepository scheduledJobRepository,
            ObjectMapper objectMapper) {
        FlowScheduleSyncHook hook = new FlowScheduleSyncHook(scheduledJobRepository, objectMapper);
        hookRegistry.register(hook);
        return hook;
    }

    // ---------------------------------------------------------------------------
    // Audit hook — records setup changes to the setup_audit_trail table
    // ---------------------------------------------------------------------------

    @Bean
    public CerbosPolicySyncHook cerbosPolicySyncHook(
            BeforeSaveHookRegistry hookRegistry,
            CerbosPolicySyncCoalescer syncCoalescer) {
        CerbosPolicySyncHook hook = new CerbosPolicySyncHook(syncCoalescer);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public FieldPermissionSyncHook fieldPermissionSyncHook(
            BeforeSaveHookRegistry hookRegistry,
            CerbosPolicySyncCoalescer syncCoalescer) {
        FieldPermissionSyncHook hook = new FieldPermissionSyncHook(syncCoalescer);
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

    // ---------------------------------------------------------------------------
    // Credential vault hooks — only registered when an EncryptionService is
    // available (i.e., kelta.encryption.key is set). The encryption hook runs
    // first (order -100) to move plaintext secrets into the encrypted blob;
    // the event publisher fires after (order 100) so all worker pods can
    // invalidate their credential caches.
    // ---------------------------------------------------------------------------

    @Bean
    @ConditionalOnProperty(name = "kelta.encryption.key")
    public CredentialEncryptionHook credentialEncryptionHook(
            BeforeSaveHookRegistry hookRegistry,
            EncryptionService encryptionService,
            CredentialTypeRegistry credentialTypeRegistry,
            ObjectMapper objectMapper) {
        CredentialEncryptionHook hook = new CredentialEncryptionHook(
                encryptionService, credentialTypeRegistry, objectMapper);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public CredentialEventPublisher credentialEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        CredentialEventPublisher hook = new CredentialEventPublisher(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.TenantEmailConfigEventPublisher tenantEmailConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        io.kelta.worker.listener.TenantEmailConfigEventPublisher hook =
                new io.kelta.worker.listener.TenantEmailConfigEventPublisher(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.TenantIpAllowlistConfigEventPublisher tenantIpAllowlistConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        io.kelta.worker.listener.TenantIpAllowlistConfigEventPublisher hook =
                new io.kelta.worker.listener.TenantIpAllowlistConfigEventPublisher(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    // ---------------------------------------------------------------------------
    // OpenAPI spec library — parser bean + NATS broadcast on spec changes
    // ---------------------------------------------------------------------------

    @Bean
    public OpenApiSpecParser openApiSpecParser(ObjectMapper objectMapper) {
        return new OpenApiSpecParser(objectMapper);
    }

    @Bean
    public ApiSpecConfigHook apiSpecConfigHook(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        ApiSpecConfigHook hook = new ApiSpecConfigHook(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public ValidationRuleRefreshHook validationRuleRefreshHook(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher,
            JdbcTemplate jdbcTemplate) {
        ValidationRuleRefreshHook hook = new ValidationRuleRefreshHook(
                eventPublisher, jdbcTemplate);
        hookRegistry.register(hook);
        return hook;
    }

    /**
     * Scope-definition gate for delegated administration: rejects delegated-admin scopes that
     * would delegate profiles/permission sets granting privileged permissions, and validates
     * referenced ids. DelegatedAdminService re-checks the privileged filter at request time.
     */
    @Bean
    public io.kelta.worker.listener.DelegatedAdminScopeValidationHook delegatedAdminScopeValidationHook(
            BeforeSaveHookRegistry hookRegistry,
            io.kelta.worker.repository.DelegatedAdminScopeRepository scopeRepository,
            tools.jackson.databind.ObjectMapper objectMapper) {
        io.kelta.worker.listener.DelegatedAdminScopeValidationHook hook =
                new io.kelta.worker.listener.DelegatedAdminScopeValidationHook(scopeRepository, objectMapper);
        hookRegistry.register(hook);
        return hook;
    }

    /**
     * Last-line write guard for identity collections (users, user-permission-sets,
     * group-memberships, delegated-admin-scopes): identified HTTP writes require MANAGE_USERS /
     * MANAGE_DELEGATED_ADMINS (or MODIFY_ALL_DATA), or a scope-validated DelegatedWriteContext.
     * Closes the unauthorized /api/operations write path for identity collections.
     */
    @Bean
    public io.kelta.worker.listener.IdentityCollectionGuardHook identityCollectionGuardHook(
            BeforeSaveHookRegistry hookRegistry,
            io.kelta.worker.repository.BootstrapRepository bootstrapRepository) {
        io.kelta.worker.listener.IdentityCollectionGuardHook hook =
                new io.kelta.worker.listener.IdentityCollectionGuardHook(bootstrapRepository);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public LayoutRuleRefreshHook layoutRuleRefreshHook(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        LayoutRuleRefreshHook hook = new LayoutRuleRefreshHook(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public PageLayoutConfigEventPublisher pageLayoutConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        PageLayoutConfigEventPublisher hook = new PageLayoutConfigEventPublisher(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.UIPageConfigEventPublisher uiPageConfigEventPublisher(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        io.kelta.worker.listener.UIPageConfigEventPublisher hook =
                new io.kelta.worker.listener.UIPageConfigEventPublisher(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public LayoutSectionRefreshHook layoutSectionRefreshHook(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        LayoutSectionRefreshHook hook = new LayoutSectionRefreshHook(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public LayoutFieldRefreshHook layoutFieldRefreshHook(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher,
            JdbcTemplate jdbcTemplate) {
        LayoutFieldRefreshHook hook = new LayoutFieldRefreshHook(eventPublisher, jdbcTemplate);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public LayoutRelatedListRefreshHook layoutRelatedListRefreshHook(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        LayoutRelatedListRefreshHook hook = new LayoutRelatedListRefreshHook(eventPublisher);
        hookRegistry.register(hook);
        return hook;
    }

    // ---------------------------------------------------------------------------
    // Approval workflow hooks and handlers
    // ---------------------------------------------------------------------------

    @Bean
    public ApprovalProcessConfigHook approvalProcessConfigHook(
            BeforeSaveHookRegistry hookRegistry,
            PlatformEventPublisher eventPublisher) {
        ApprovalProcessConfigHook hook = new ApprovalProcessConfigHook(eventPublisher);
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
    public io.kelta.worker.listener.AttachmentCleanupHook attachmentCleanupHook(
            BeforeSaveHookRegistry hookRegistry,
            JdbcTemplate jdbcTemplate,
            io.kelta.worker.service.S3StorageService storageService) {
        io.kelta.worker.listener.AttachmentCleanupHook hook =
                new io.kelta.worker.listener.AttachmentCleanupHook(jdbcTemplate, storageService);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.RecordTombstoneHook recordTombstoneHook(
            BeforeSaveHookRegistry hookRegistry,
            io.kelta.worker.repository.RecordTombstoneRepository tombstoneRepository,
            CollectionRegistry collectionRegistry) {
        io.kelta.worker.listener.RecordTombstoneHook hook =
                new io.kelta.worker.listener.RecordTombstoneHook(tombstoneRepository, collectionRegistry);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.FieldHistoryHook fieldHistoryHook(
            BeforeSaveHookRegistry hookRegistry,
            io.kelta.worker.repository.FieldHistoryRepository fieldHistoryRepository,
            CollectionRegistry collectionRegistry,
            CollectionLifecycleManager lifecycleManager,
            QueryEngine queryEngine,
            ObjectMapper objectMapper) {
        io.kelta.worker.listener.FieldHistoryHook hook =
                new io.kelta.worker.listener.FieldHistoryHook(fieldHistoryRepository, collectionRegistry,
                        lifecycleManager, queryEngine, objectMapper);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public RecordTypeEnforcementHook recordTypeEnforcementHook(
            BeforeSaveHookRegistry hookRegistry,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        RecordTypeEnforcementHook hook = new RecordTypeEnforcementHook(jdbcTemplate, objectMapper);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.EmbeddingOnWriteHook embeddingOnWriteHook(
            BeforeSaveHookRegistry hookRegistry,
            CollectionRegistry collectionRegistry,
            io.kelta.runtime.embedding.EmbeddingService embeddingService) {
        io.kelta.worker.listener.EmbeddingOnWriteHook hook =
                new io.kelta.worker.listener.EmbeddingOnWriteHook(collectionRegistry, embeddingService);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public io.kelta.worker.listener.FormulaComputeHook formulaComputeHook(
            BeforeSaveHookRegistry hookRegistry,
            CollectionRegistry collectionRegistry,
            FormulaEvaluator formulaEvaluator) {
        io.kelta.worker.listener.FormulaComputeHook hook =
                new io.kelta.worker.listener.FormulaComputeHook(collectionRegistry, formulaEvaluator);
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
