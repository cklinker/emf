package io.kelta.gateway.listener;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code kelta.config.tenant.ip-allowlist.changed.*} (broadcast) and
 * refreshes the gateway's per-tenant IP allowlist cache from the worker so new ranges
 * take effect without a restart. Every gateway pod runs this listener so the fleet
 * stays consistent (Critical Rule 1).
 *
 * <p>The event carries only a tenantId; the config itself is small and rarely changes,
 * so we simply re-pull the full allowlist map rather than tracking per-tenant deltas.
 */
@Component
public class IpAllowlistCacheInvalidationListener {

    private static final Logger log =
            LoggerFactory.getLogger(IpAllowlistCacheInvalidationListener.class);

    private final GatewayCacheManager cacheManager;

    public IpAllowlistCacheInvalidationListener(GatewayCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void handleIpAllowlistChanged(String message) {
        try {
            log.info("Tenant IP allowlist changed — refreshing gateway allowlist cache from worker");
            cacheManager.refreshIpAllowlistsFromWorker();
        } catch (Exception e) {
            log.error("Failed to process IP allowlist changed event: {}", e.getMessage(), e);
        }
    }
}
