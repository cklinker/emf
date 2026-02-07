package com.emf.controlplane.dto;

import com.emf.controlplane.entity.DashboardComponent;
import com.emf.controlplane.entity.UserDashboard;

import java.time.Instant;
import java.util.List;

public class UserDashboardDto {

    private String id;
    private String name;
    private String description;
    private String folderId;
    private String accessLevel;
    private boolean dynamic;
    private String runningUserId;
    private int columnCount;
    private String createdBy;
    private List<ComponentDto> components;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserDashboardDto fromEntity(UserDashboard entity) {
        UserDashboardDto dto = new UserDashboardDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setFolderId(entity.getFolder() != null ? entity.getFolder().getId() : null);
        dto.setAccessLevel(entity.getAccessLevel());
        dto.setDynamic(entity.isDynamic());
        dto.setRunningUserId(entity.getRunningUserId());
        dto.setColumnCount(entity.getColumnCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getComponents() != null) {
            dto.setComponents(entity.getComponents().stream()
                    .map(ComponentDto::fromEntity).toList());
        }
        return dto;
    }

    public static class ComponentDto {
        private String id;
        private String reportId;
        private String componentType;
        private String title;
        private int columnPosition;
        private int rowPosition;
        private int columnSpan;
        private int rowSpan;
        private String config;
        private int sortOrder;

        public static ComponentDto fromEntity(DashboardComponent entity) {
            ComponentDto dto = new ComponentDto();
            dto.setId(entity.getId());
            dto.setReportId(entity.getReport().getId());
            dto.setComponentType(entity.getComponentType());
            dto.setTitle(entity.getTitle());
            dto.setColumnPosition(entity.getColumnPosition());
            dto.setRowPosition(entity.getRowPosition());
            dto.setColumnSpan(entity.getColumnSpan());
            dto.setRowSpan(entity.getRowSpan());
            dto.setConfig(entity.getConfig());
            dto.setSortOrder(entity.getSortOrder());
            return dto;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }
        public String getComponentType() { return componentType; }
        public void setComponentType(String componentType) { this.componentType = componentType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public int getColumnPosition() { return columnPosition; }
        public void setColumnPosition(int columnPosition) { this.columnPosition = columnPosition; }
        public int getRowPosition() { return rowPosition; }
        public void setRowPosition(int rowPosition) { this.rowPosition = rowPosition; }
        public int getColumnSpan() { return columnSpan; }
        public void setColumnSpan(int columnSpan) { this.columnSpan = columnSpan; }
        public int getRowSpan() { return rowSpan; }
        public void setRowSpan(int rowSpan) { this.rowSpan = rowSpan; }
        public String getConfig() { return config; }
        public void setConfig(String config) { this.config = config; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
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
    public List<ComponentDto> getComponents() { return components; }
    public void setComponents(List<ComponentDto> components) { this.components = components; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
