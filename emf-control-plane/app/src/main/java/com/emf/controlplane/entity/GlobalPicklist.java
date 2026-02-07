package com.emf.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "global_picklist")
public class GlobalPicklist extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sorted")
    private boolean sorted = false;

    @Column(name = "restricted")
    private boolean restricted = true;

    public GlobalPicklist() {
        super();
    }

    public GlobalPicklist(String tenantId, String name) {
        super();
        this.tenantId = tenantId;
        this.name = name;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSorted() { return sorted; }
    public void setSorted(boolean sorted) { this.sorted = sorted; }

    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }
}
