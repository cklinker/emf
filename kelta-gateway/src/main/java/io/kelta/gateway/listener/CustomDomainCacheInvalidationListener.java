package io.kelta.gateway.listener;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.domain.changed.*} (broadcast) and evicts
 * the matching entry from {@link GatewayCacheManager}'s custom domain cache.
 *
 * <p>The gateway caches both positive (domain → tenantSlug) and negative
 * (domain → NOT_FOUND) lookups. Either kind must be dropped when the upstream
 * row changes so the next request re-resolves against the worker.
 */
@Component
public class CustomDomainCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CustomDomainCacheInvalidationListener.class);

    private final GatewayCacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public CustomDomainCacheInvalidationListener(GatewayCacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    public void handleDomainChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                payload = root;
            }
            String domain = textOrNull(payload.get("domain"));
            if (domain == null) {
                log.warn("Custom domain changed event missing 'domain' field — evicting entire cache");
                cacheManager.evictAllCustomDomains();
                return;
            }
            log.info("Custom domain {} changed — evicting gateway cache entry", domain);
            cacheManager.removeCustomDomain(domain);
        } catch (Exception e) {
            log.error("Failed to process custom domain changed event: {}", e.getMessage(), e);
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
