package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.feature.changed.*} (broadcast) and evicts
 * tenant-scoped feature/limit cache entries from {@link WorkerCacheManager}.
 *
 * <p>System feature toggles are stored per tenant. When a toggle changes,
 * every worker pod drops its cached governor/limits snapshot for the affected
 * tenant so the next read re-fetches from the database.
 */
@Component
public class SystemFeatureCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(SystemFeatureCacheInvalidationListener.class);

    private final WorkerCacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public SystemFeatureCacheInvalidationListener(WorkerCacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    public void handleFeatureChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String tenantId = textOrNull(root.get("tenantId"));
            if (tenantId == null) {
                JsonNode payload = root.get("payload");
                if (payload != null && !payload.isNull()) {
                    tenantId = textOrNull(payload.get("tenantId"));
                }
            }
            if (tenantId == null) {
                log.warn("Skipping feature cache invalidation: missing tenantId in event");
                return;
            }
            log.info("System feature changed for tenant {} — evicting local tenant limits cache", tenantId);
            cacheManager.evictTenantLimits(tenantId);
        } catch (Exception e) {
            log.error("Failed to process feature changed event: {}", e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.stringValue();
        return value == null || value.isBlank() ? null : value;
    }
}
