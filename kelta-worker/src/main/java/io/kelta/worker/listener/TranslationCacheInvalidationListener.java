package io.kelta.worker.listener;

import io.kelta.runtime.router.SystemCollectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.translation.changed.*} (broadcast) and evicts
 * the {@code ui-translations} response entries from the {@link SystemCollectionCache}
 * so every pod serves fresh tenant translations after a write anywhere in the fleet
 * (tenant i18n authoring, app-intelligence slice 4). Published by
 * {@link TranslationConfigEventPublisher}.
 */
@Component
public class TranslationCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(TranslationCacheInvalidationListener.class);

    private final SystemCollectionCache systemCollectionCache;
    private final ObjectMapper objectMapper;

    public TranslationCacheInvalidationListener(SystemCollectionCache systemCollectionCache,
                                                ObjectMapper objectMapper) {
        this.systemCollectionCache = systemCollectionCache;
        this.objectMapper = objectMapper;
    }

    public void handleTranslationChanged(String message) {
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
                log.warn("Skipping translation invalidation: missing tenantId in event");
                return;
            }

            log.info("Translations changed for tenant {} — evicting response cache", tenantId);
            systemCollectionCache.evict(tenantId, "ui-translations");
        } catch (Exception e) {
            log.error("Failed to process translation changed event: {}", e.getMessage(), e);
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
