package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents a sharing rule that grants record access to roles or groups.
 * Rules can be owner-based (share records owned by one role with another)
 * or criteria-based (share records matching field conditions).
 */
@Entity
@Table(name = "sharing_rule")
@EntityListeners(AuditingEntityListener.class)
public class SharingRule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "rule_type", nullable = false, length = 20)
    private String ruleType; // OWNER_BASED or CRITERIA_BASED

    @Column(name = "shared_from", length = 36)
    private String sharedFrom; // role or group that owns records

    @Column(name = "shared_to", nullable = false, length = 36)
    private String sharedTo; // role or group receiving access

    @Column(name = "shared_to_type", nullable = false, length = 20)
    private String sharedToType; // ROLE, GROUP, QUEUE

    @Column(name = "access_level", nullable = false, length = 20)
    private String accessLevel; // READ or READ_WRITE

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria", columnDefinition = "jsonb")
    private String criteria; // for CRITERIA_BASED rules

    @Column(name = "active")
    private boolean active = true;

    public SharingRule() {
        super();
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getSharedFrom() { return sharedFrom; }
    public void setSharedFrom(String sharedFrom) { this.sharedFrom = sharedFrom; }

    public String getSharedTo() { return sharedTo; }
    public void setSharedTo(String sharedTo) { this.sharedTo = sharedTo; }

    public String getSharedToType() { return sharedToType; }
    public void setSharedToType(String sharedToType) { this.sharedToType = sharedToType; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

    public String getCriteria() { return criteria; }
    public void setCriteria(String criteria) { this.criteria = criteria; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
