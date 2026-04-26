package io.kelta.worker.listener;

import io.kelta.worker.service.credential.CredentialResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscribes to {@code kelta.config.credential.changed.>} (broadcast) and
 * drops cached resolutions for the credential in question. Every worker pod
 * runs this listener so cache stays consistent across the fleet.
 */
@Component
public class CredentialCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CredentialCacheInvalidationListener.class);

    private final CredentialResolver resolver;
    private final ObjectMapper objectMapper;

    public CredentialCacheInvalidationListener(CredentialResolver resolver, ObjectMapper objectMapper) {
        this.resolver = resolver;
        this.objectMapper = objectMapper;
    }

    public void handleCredentialChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            // Event envelope: { type, tenantId, payload: { id, name, type, changeType } }
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                payload = root;
            }
            JsonNode idNode = payload.get("id");
            if (idNode == null || idNode.isNull() || !idNode.isTextual()) {
                log.warn("Skipping credential invalidation: missing id in payload");
                return;
            }
            String credentialId = idNode.stringValue();
            log.info("Credential {} changed — invalidating local resolver cache", credentialId);
            resolver.invalidate(credentialId);
        } catch (Exception e) {
            log.error("Failed to process credential changed event: {}", e.getMessage(), e);
        }
    }
}
