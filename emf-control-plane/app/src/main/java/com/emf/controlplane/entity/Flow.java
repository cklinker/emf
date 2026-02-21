package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "flow")
public class Flow extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "flow_type", nullable = false, length = 30)
    private String flowType;

    @Column(name = "active")
    private boolean active = false;

    @Column(name = "version")
    private int version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_config", columnDefinition = "jsonb")
    private String triggerConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false, columnDefinition = "jsonb")
    private String definition;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    public Flow() { super(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getTriggerConfig() { return triggerConfig; }
    public void setTriggerConfig(String triggerConfig) { this.triggerConfig = triggerConfig; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
