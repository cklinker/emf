package io.kelta.worker.config;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.module.ModuleContext;
import io.kelta.worker.module.JdbcModuleStore;
import io.kelta.worker.module.ModuleConfigEventPublisher;
import io.kelta.worker.module.ModuleJarService;
import io.kelta.worker.module.RuntimeModuleManager;
import io.kelta.worker.service.S3StorageService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.springframework.lang.Nullable;

/**
 * Spring configuration for runtime module loading.
 * <p>
 * Wires the {@link RuntimeModuleManager} with its dependencies and
 * loads all active modules on application startup.
 * <p>
 * When S3 storage is available, modules can be loaded from their JAR files
 * via sandboxed ClassLoaders. Without S3, stub handlers are used.
 * <p>
 * Enabled by default. Disable with {@code kelta.modules.runtime.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "kelta.modules.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class ModuleConfig {

    private static final Logger log = LoggerFactory.getLogger(ModuleConfig.class);

    @Bean
    public ModuleStore moduleStore(JdbcTemplate jdbcTemplate) {
        return new JdbcModuleStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(S3StorageService.class)
    public ModuleJarService moduleJarService(S3StorageService s3StorageService) {
        return new ModuleJarService(s3StorageService);
    }

    @Bean
    public RuntimeModuleManager runtimeModuleManager(ModuleStore moduleStore,
                                                       ActionHandlerRegistry actionHandlerRegistry,
                                                       ObjectMapper objectMapper,
                                                       @Nullable ModuleJarService jarService,
                                                       QueryEngine queryEngine,
                                                       CollectionRegistry collectionRegistry,
                                                       @Nullable FormulaEvaluator formulaEvaluator) {
        ModuleContext moduleContext = new ModuleContext(
            queryEngine, collectionRegistry, formulaEvaluator,
            objectMapper, actionHandlerRegistry, null);

        if (jarService != null) {
            log.info("Runtime module JAR loading enabled (S3 storage available)");
        } else {
            log.info("Runtime module JAR loading disabled (S3 storage not available, using stub handlers)");
        }

        return new RuntimeModuleManager(moduleStore, actionHandlerRegistry, objectMapper,
            jarService, moduleContext);
    }

    @Bean
    public ModuleConfigEventPublisher moduleConfigEventPublisher(
            PlatformEventPublisher eventPublisher) {
        return new ModuleConfigEventPublisher(eventPublisher);
    }

    /**
     * Loads all active runtime modules when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        // Reading all active modules is an explicit cross-tenant operation;
        // bind the platform sentinel so RLS allows the query without falling
        // back to a blank tenant context.
        TenantContext.runAsPlatform(() -> {
            try {
                RuntimeModuleManager manager = event.getApplicationContext()
                    .getBean(RuntimeModuleManager.class);
                log.info("Loading active runtime modules on startup...");
                manager.loadAllActiveModules();
            } catch (Exception e) {
                log.warn("Could not load runtime modules on startup: {}", e.getMessage());
            }
        });
    }
}
