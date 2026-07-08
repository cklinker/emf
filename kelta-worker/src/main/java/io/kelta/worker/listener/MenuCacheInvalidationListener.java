package io.kelta.worker.listener;

import io.kelta.runtime.router.SystemCollectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.menu.changed.*} (broadcast) and evicts the
 * {@code ui-menus} / {@code ui-menu-items} response entries from the
 * {@link SystemCollectionCache}, so every pod serves fresh navigation config after a
 * menu write anywhere in the fleet (apps/nav v2). Published by
 * {@link MenuConfigEventPublisher}.
 *
 * <p>Subject pattern is {@code kelta.config.menu.changed.<tenantId>}; the tenant id is
 * read from the {@link io.kelta.runtime.event.PlatformEvent} envelope.
 */
@Component
public class MenuCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(MenuCacheInvalidationListener.class);

    private final SystemCollectionCache systemCollectionCache;
    private final ObjectMapper objectMapper;

    public MenuCacheInvalidationListener(SystemCollectionCache systemCollectionCache,
                                         ObjectMapper objectMapper) {
        this.systemCollectionCache = systemCollectionCache;
        this.objectMapper = objectMapper;
    }

    public void handleMenuChanged(String message) {
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
                log.warn("Skipping menu invalidation: missing tenantId in event");
                return;
            }

            log.info("Menu config changed for tenant {} — evicting menu response caches", tenantId);
            systemCollectionCache.evict(tenantId, "ui-menus");
            systemCollectionCache.evict(tenantId, "ui-menu-items");
        } catch (Exception e) {
            log.error("Failed to process menu changed event: {}", e.getMessage(), e);
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
