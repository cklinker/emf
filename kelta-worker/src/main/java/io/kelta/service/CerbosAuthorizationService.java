package io.kelta.worker.service;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CerbosAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CerbosAuthorizationService.class);

    private final CerbosBlockingClient cerbosClient;

    public CerbosAuthorizationService(CerbosBlockingClient cerbosClient) {
        this.cerbosClient = cerbosClient;
    }

    public boolean checkRecordAccess(String email, String profileId, String tenantId,
                                      String collectionId, String recordId,
                                      Map<String, Object> recordAttributes, String action) {
        Principal principal = Principal.newInstance(email, "user")
                .withAttribute("profileId", stringAttr(profileId))
                .withAttribute("tenantId", stringAttr(tenantId));

        Resource resource = Resource.newInstance("record", recordId)
                .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                .withAttribute("tenantId", stringAttr(tenantId));

        if (recordAttributes != null) {
            for (Map.Entry<String, Object> entry : recordAttributes.entrySet()) {
                if (entry.getValue() != null) {
                    resource = resource.withAttribute(entry.getKey(), toAttributeValue(entry.getValue()));
                }
            }
        }

        resource = resource.withScope(tenantId);

        try {
            CheckResult result = cerbosClient.check(principal, resource, action);
            boolean allowed = result.isAllowed(action);
            log.debug("Cerbos record check: user={} collection={} record={} action={} allowed={}",
                    email, collectionId, recordId, action, allowed);
            return allowed;
        } catch (Exception e) {
            log.error("Cerbos check failed for user={} record={}: {}", email, recordId, e.getMessage());
            return false;
        }
    }

    public boolean checkFieldAccess(String email, String profileId, String tenantId,
                                     String collectionId, String fieldId, String action) {
        Principal principal = Principal.newInstance(email, "user")
                .withAttribute("profileId", stringAttr(profileId))
                .withAttribute("tenantId", stringAttr(tenantId));

        Resource resource = Resource.newInstance("field", fieldId)
                .withAttribute("collectionId", AttributeValue.stringValue(collectionId))
                .withAttribute("fieldId", AttributeValue.stringValue(fieldId))
                .withScope(tenantId);

        try {
            CheckResult result = cerbosClient.check(principal, resource, action);
            boolean allowed = result.isAllowed(action);
            log.debug("Cerbos field check: user={} collection={} field={} action={} allowed={}",
                    email, collectionId, fieldId, action, allowed);
            return allowed;
        } catch (Exception e) {
            log.error("Cerbos field check failed for user={} field={}: {}", email, fieldId, e.getMessage());
            return false;
        }
    }

    public List<String> batchCheckFieldAccess(String email, String profileId, String tenantId,
                                               String collectionId, List<String> fieldIds, String action) {
        return fieldIds.stream()
                .filter(fieldId -> checkFieldAccess(email, profileId, tenantId, collectionId, fieldId, action))
                .toList();
    }

    private static AttributeValue stringAttr(String value) {
        return AttributeValue.stringValue(value != null ? value : "");
    }

    private static AttributeValue toAttributeValue(Object value) {
        if (value instanceof String s) return AttributeValue.stringValue(s);
        if (value instanceof Number n) return AttributeValue.doubleValue(n.doubleValue());
        if (value instanceof Boolean b) return AttributeValue.boolValue(b);
        return AttributeValue.stringValue(String.valueOf(value));
    }
}
