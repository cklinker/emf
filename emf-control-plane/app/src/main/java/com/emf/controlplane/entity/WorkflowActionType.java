package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_action_type")
public class WorkflowActionType extends BaseEntity {

    @Column(name = "key", nullable = false, unique = true, length = 50)
    private String key;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_schema", columnDefinition = "jsonb")
    private String configSchema;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "handler_class", nullable = false, length = 255)
    private String handlerClass;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "built_in")
    private boolean builtIn = true;

    public WorkflowActionType() { super(); }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getConfigSchema() { return configSchema; }
    public void setConfigSchema(String configSchema) { this.configSchema = configSchema; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getHandlerClass() { return handlerClass; }
    public void setHandlerClass(String handlerClass) { this.handlerClass = handlerClass; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
}
