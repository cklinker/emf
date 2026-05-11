package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.feature.changed.*} (broadcast) and evicts
 * tenant-scoped caches whose contents may be derived from system feature
 * toggles. Every worker pod runs this listener so cache stays consistent
 * across the fleet.
 *
 * <p>Subject pattern is {@code kelta.config.feature.changed.<tenantId>}; the
 * tenant id is extracted from the {@code tenantId} field of the
 * {@link io.kelta.runtime.event.PlatformEvent} envelope. Currently evicts the
 * tenant limits cache, since governor limits are the cached value most likely
 * to depend on feature flags. Additional caches can be evicted here as new
 * feature-derived caches are introduced.
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
                log.warn("Skipping feature invalidation: missing tenantId in event");
                return;
            }

            log.info("System feature changed for tenant {} — evicting tenant limits cache", tenantId);
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
        return value.isBlank() ? null : value;
    }
}
