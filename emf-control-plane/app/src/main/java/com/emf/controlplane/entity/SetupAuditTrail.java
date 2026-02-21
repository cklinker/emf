package com.emf.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Tracks all configuration changes made through the control plane.
 */
@Entity
@Table(name = "setup_audit_trail")
public class SetupAuditTrail extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "section", nullable = false, length = 100)
    private String section;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public SetupAuditTrail() {
        super();
        this.timestamp = Instant.now();
    }

    public SetupAuditTrail(String tenantId, String userId, String action, String section,
                           String entityType, String entityId, String entityName,
                           String oldValue, String newValue) {
        super(tenantId);
        this.timestamp = Instant.now();
        this.userId = userId;
        this.action = action;
        this.section = section;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityName = entityName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
