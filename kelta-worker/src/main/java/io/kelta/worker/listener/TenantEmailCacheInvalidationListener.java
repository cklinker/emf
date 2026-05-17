package io.kelta.worker.listener;

import io.kelta.worker.service.email.SmtpEmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.tenant.email.changed.>} and evicts the
 * {@link SmtpEmailProvider} sender cache for the affected tenant.
 *
 * <p>Also subscribes to {@code kelta.config.credential.changed.>} and clears
 * <em>all</em> tenant senders when any credential changes — we don't track
 * which tenants reference a given credential, and the cache is small (max 100,
 * 5-min TTL), so a broad eviction is cheaper than maintaining the index.
 */
@Component
@ConditionalOnProperty(name = "kelta.email.enabled", havingValue = "true", matchIfMissing = true)
public class TenantEmailCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(TenantEmailCacheInvalidationListener.class);

    private final SmtpEmailProvider smtpEmailProvider;
    private final ObjectMapper objectMapper;

    public TenantEmailCacheInvalidationListener(SmtpEmailProvider smtpEmailProvider,
                                                 ObjectMapper objectMapper) {
        this.smtpEmailProvider = smtpEmailProvider;
        this.objectMapper = objectMapper;
    }

    public void handleTenantEmailChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                payload = root;
            }
            JsonNode tenantNode = payload.get("tenantId");
            if (tenantNode == null || !tenantNode.isTextual()) {
                log.warn("Skipping tenant-email invalidation: payload missing tenantId");
                return;
            }
            String tenantId = tenantNode.stringValue();
            log.info("Tenant {} email config changed — evicting SMTP sender cache", tenantId);
            smtpEmailProvider.evictTenant(tenantId);
        } catch (Exception e) {
            log.error("Failed to process tenant email changed event: {}", e.getMessage(), e);
        }
    }

    public void handleCredentialChanged(String message) {
        // Coarse: any credential change drops all tenant senders.
        // Cheap because cache is small and senders re-init lazily on the next send.
        smtpEmailProvider.evictAll();
        log.debug("Credential changed — evicted all tenant SMTP sender caches");
    }
}
