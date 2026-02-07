package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dashboard")
public class UserDashboard extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private ReportFolder folder;

    @Column(name = "access_level", length = 20)
    private String accessLevel = "PRIVATE";

    @Column(name = "is_dynamic")
    private boolean dynamic;

    @Column(name = "running_user_id", length = 36)
    private String runningUserId;

    @Column(name = "column_count")
    private int columnCount = 3;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<DashboardComponent> components = new ArrayList<>();

    public UserDashboard() { super(); }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ReportFolder getFolder() { return folder; }
    public void setFolder(ReportFolder folder) { this.folder = folder; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public boolean isDynamic() { return dynamic; }
    public void setDynamic(boolean dynamic) { this.dynamic = dynamic; }
    public String getRunningUserId() { return runningUserId; }
    public void setRunningUserId(String runningUserId) { this.runningUserId = runningUserId; }
    public int getColumnCount() { return columnCount; }
    public void setColumnCount(int columnCount) { this.columnCount = columnCount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public List<DashboardComponent> getComponents() { return components; }
    public void setComponents(List<DashboardComponent> components) { this.components = components; }
}
