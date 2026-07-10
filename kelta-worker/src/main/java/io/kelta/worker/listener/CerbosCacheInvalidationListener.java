package io.kelta.worker.listener;

import tools.jackson.databind.ObjectMapper;
import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.RecordRuleIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * NATS listener that invalidates the worker's Cerbos authorization caches
 * (field access, collection-wide record decisions, custom-rule index) when
 * policies are re-synced for a tenant.
 *
 * <p>Listens to {@code kelta.cerbos.policies.changed} events published by
 * {@code CerbosPolicySyncService} after pushing updated policies to Cerbos.
 */
@Component
public class CerbosCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CerbosCacheInvalidationListener.class);

    private final CerbosAuthorizationService authzService;
    private final RecordRuleIndex recordRuleIndex;
    private final ObjectMapper objectMapper;

    public CerbosCacheInvalidationListener(CerbosAuthorizationService authzService,
                                            RecordRuleIndex recordRuleIndex,
                                            ObjectMapper objectMapper) {
        this.authzService = authzService;
        this.recordRuleIndex = recordRuleIndex;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public void handlePolicyChanged(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String tenantId = (String) payload.get("tenantId");

            if (tenantId == null) {
                log.warn("Received policy changed event without tenantId: {}", message);
                return;
            }

            log.info("Cerbos policies changed for tenant {} — evicting worker authz caches", tenantId);
            authzService.evictForTenant(tenantId);
            recordRuleIndex.evictTenant(tenantId);
        } catch (Exception e) {
            log.error("Failed to process policy changed event: {}", e.getMessage(), e);
        }
    }
}
