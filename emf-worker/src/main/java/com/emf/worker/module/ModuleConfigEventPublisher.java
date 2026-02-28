package com.emf.worker.module;

import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.EventFactory;
import com.emf.runtime.event.ModuleChangeType;
import com.emf.runtime.event.ModuleChangedPayload;
import com.emf.runtime.module.TenantModuleData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publishes module lifecycle events to Kafka for cross-pod propagation.
 * <p>
 * When a module is installed, enabled, disabled, or uninstalled on one pod,
 * this publisher sends an event so all other pods can update their handler registries.
 * <p>
 * Follows the same pattern as {@link com.emf.worker.listener.CollectionConfigEventPublisher}.
 *
 * @since 1.0.0
 */
public class ModuleConfigEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ModuleConfigEventPublisher.class);

    static final String TOPIC = "emf.config.module.changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ModuleConfigEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a module lifecycle change event.
     *
     * @param module     the module data
     * @param changeType the type of change
     */
    public void publishEvent(TenantModuleData module, ModuleChangeType changeType) {
        ModuleChangedPayload payload = new ModuleChangedPayload(
            module.id(), module.tenantId(), module.moduleId(), module.name(),
            module.version(), null, module.moduleClass(),
            module.manifest(), changeType
        );

        try {
            ConfigEvent<ModuleChangedPayload> event = EventFactory.createEvent(TOPIC, payload);
            String json = objectMapper.writeValueAsString(event);
            String key = module.tenantId() + ":" + module.moduleId();

            kafkaTemplate.send(TOPIC, key, json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish module {} event for '{}' (tenant={}): {}",
                            changeType, module.moduleId(), module.tenantId(), ex.getMessage());
                    } else {
                        log.info("Published module {} event for '{}' v{} (tenant={}) to topic '{}'",
                            changeType, module.moduleId(), module.version(),
                            module.tenantId(), TOPIC);
                    }
                });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize module changed event for '{}' (tenant={}): {}",
                module.moduleId(), module.tenantId(), e.getMessage());
        }
    }
}
