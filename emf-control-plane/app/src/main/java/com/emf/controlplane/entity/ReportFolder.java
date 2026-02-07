package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "report_folder")
public class ReportFolder extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "access_level", length = 20)
    private String accessLevel = "PRIVATE";

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    public ReportFolder() { super(); }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
