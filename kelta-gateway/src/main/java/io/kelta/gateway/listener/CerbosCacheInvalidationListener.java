package io.kelta.gateway.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.gateway.authz.cerbos.CerbosAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka listener that invalidates the gateway's Cerbos permission cache
 * when policies are re-synced for a tenant.
 *
 * <p>Listens to {@code kelta.cerbos.policies.changed} events published by
 * {@code CerbosPolicySyncService} after pushing updated policies to Cerbos.
 */
@Component
public class CerbosCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CerbosCacheInvalidationListener.class);

    private final CerbosAuthorizationService authzService;
    private final ObjectMapper objectMapper;

    public CerbosCacheInvalidationListener(CerbosAuthorizationService authzService,
                                            ObjectMapper objectMapper) {
        this.authzService = authzService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "kelta.cerbos.policies.changed",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "recordEventKafkaListenerContainerFactory"
    )
    @SuppressWarnings("unchecked")
    public void handlePolicyChanged(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String tenantId = (String) payload.get("tenantId");

            if (tenantId == null) {
                log.warn("Received policy changed event without tenantId: {}", message);
                return;
            }

            log.info("Cerbos policies changed for tenant {} — evicting gateway cache", tenantId);
            authzService.evictForTenant(tenantId);
        } catch (Exception e) {
            log.error("Failed to process policy changed event: {}", e.getMessage(), e);
        }
    }
}
