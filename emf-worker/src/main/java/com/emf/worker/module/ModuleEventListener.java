package com.emf.worker.module;

import com.emf.runtime.event.ModuleChangeType;
import com.emf.runtime.event.ModuleChangedPayload;
import com.emf.runtime.module.ModuleStore;
import com.emf.runtime.module.TenantModuleData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Kafka listener for module lifecycle change events.
 * <p>
 * When a module is installed/enabled/disabled/uninstalled on any pod,
 * this listener receives the event and updates the local handler registry.
 * <p>
 * Uses a unique consumer group per pod so every pod receives every event.
 *
 * @since 1.0.0
 */
@Component
public class ModuleEventListener {

    private static final Logger log = LoggerFactory.getLogger(ModuleEventListener.class);

    private final RuntimeModuleManager runtimeModuleManager;
    private final ModuleStore moduleStore;
    private final ObjectMapper objectMapper;

    public ModuleEventListener(RuntimeModuleManager runtimeModuleManager,
                                ModuleStore moduleStore,
                                ObjectMapper objectMapper) {
        this.runtimeModuleManager = runtimeModuleManager;
        this.moduleStore = moduleStore;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${emf.kafka.topics.module-changed:emf.config.module.changed}",
        groupId = "${emf.worker.id:emf-worker-default}-modules",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleModuleChanged(String message) {
        log.debug("Received module changed event: {}", message);

        try {
            ModuleChangedPayload payload = parsePayload(message);
            if (payload == null) {
                log.warn("Could not parse module changed event from message");
                return;
            }

            String tenantId = payload.getTenantId();
            String moduleId = payload.getModuleId();
            ModuleChangeType changeType = payload.getChangeType();

            log.info("Processing module {} event for '{}' (tenant={})",
                changeType, moduleId, tenantId);

            switch (changeType) {
                case INSTALLED, ENABLED -> {
                    Optional<TenantModuleData> module =
                        moduleStore.findByTenantAndModuleId(tenantId, moduleId);
                    if (module.isPresent()) {
                        runtimeModuleManager.loadModule(tenantId, module.get());
                    } else {
                        log.warn("Module '{}' not found in DB for tenant {}", moduleId, tenantId);
                    }
                }
                case DISABLED, UNINSTALLED -> {
                    Optional<TenantModuleData> module =
                        moduleStore.findByTenantAndModuleId(tenantId, moduleId);
                    if (module.isPresent()) {
                        runtimeModuleManager.unloadModule(tenantId, module.get());
                    } else {
                        log.debug("Module '{}' already removed for tenant {}", moduleId, tenantId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing module changed event: {}", e.getMessage(), e);
        }
    }

    private ModuleChangedPayload parsePayload(String message) {
        try {
            var tree = objectMapper.readTree(message);
            if (tree.has("payload")) {
                var payloadNode = tree.get("payload");
                return objectMapper.treeToValue(payloadNode, ModuleChangedPayload.class);
            }
            return objectMapper.readValue(message, ModuleChangedPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse module changed event: {}", e.getMessage());
            return null;
        }
    }
}
