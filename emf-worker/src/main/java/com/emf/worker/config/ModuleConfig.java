package com.emf.worker.config;

import com.emf.runtime.module.ModuleStore;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.emf.worker.module.JdbcModuleStore;
import com.emf.worker.module.ModuleConfigEventPublisher;
import com.emf.worker.module.RuntimeModuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring configuration for runtime module loading.
 * <p>
 * Wires the {@link RuntimeModuleManager} with its dependencies and
 * loads all active modules on application startup.
 * <p>
 * Enabled by default. Disable with {@code emf.modules.runtime.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "emf.modules.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class ModuleConfig {

    private static final Logger log = LoggerFactory.getLogger(ModuleConfig.class);

    @Bean
    public ModuleStore moduleStore(JdbcTemplate jdbcTemplate) {
        return new JdbcModuleStore(jdbcTemplate);
    }

    @Bean
    public RuntimeModuleManager runtimeModuleManager(ModuleStore moduleStore,
                                                       ActionHandlerRegistry actionHandlerRegistry,
                                                       ObjectMapper objectMapper) {
        return new RuntimeModuleManager(moduleStore, actionHandlerRegistry, objectMapper);
    }

    @Bean
    public ModuleConfigEventPublisher moduleConfigEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        return new ModuleConfigEventPublisher(kafkaTemplate, objectMapper);
    }

    /**
     * Loads all active runtime modules when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        try {
            RuntimeModuleManager manager = event.getApplicationContext()
                .getBean(RuntimeModuleManager.class);
            log.info("Loading active runtime modules on startup...");
            manager.loadAllActiveModules();
        } catch (Exception e) {
            log.warn("Could not load runtime modules on startup: {}", e.getMessage());
        }
    }
}
