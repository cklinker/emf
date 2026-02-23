package com.emf.controlplane.event;

import com.emf.controlplane.entity.WorkflowRule;
import com.emf.runtime.event.ChangeType;

import java.time.Instant;

/**
 * Payload for workflow rule changed events published to Kafka.
 * Contains the essential fields of the workflow rule that changed,
 * plus the change type (CREATED/UPDATED/DELETED) and tenant/collection
 * context for cache invalidation.
 */
public class WorkflowRuleChangedPayload {

    private String ruleId;
    private String tenantId;
    private String collectionId;
    private String name;
    private boolean active;
    private String triggerType;
    private ChangeType changeType;
    private Instant timestamp;

    public WorkflowRuleChangedPayload() {
    }

    /**
     * Creates a payload from a workflow rule entity and change type.
     * Must be called within a transaction to access lazy-loaded fields.
     */
    public static WorkflowRuleChangedPayload create(WorkflowRule rule, ChangeType changeType) {
        WorkflowRuleChangedPayload payload = new WorkflowRuleChangedPayload();
        payload.setRuleId(rule.getId());
        payload.setTenantId(rule.getTenantId());
        payload.setCollectionId(rule.getCollection().getId());
        payload.setName(rule.getName());
        payload.setActive(rule.isActive());
        payload.setTriggerType(rule.getTriggerType());
        payload.setChangeType(changeType);
        payload.setTimestamp(Instant.now());
        return payload;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "WorkflowRuleChangedPayload{" +
                "ruleId='" + ruleId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", name='" + name + '\'' +
                ", changeType=" + changeType +
                ", timestamp=" + timestamp +
                '}';
    }
}
