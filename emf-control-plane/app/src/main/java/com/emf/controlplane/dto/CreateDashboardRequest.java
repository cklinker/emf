package com.emf.controlplane.dto;

import java.util.List;

public class CreateDashboardRequest {

    private String name;
    private String description;
    private String folderId;
    private String accessLevel;
    private boolean dynamic;
    private String runningUserId;
    private int columnCount = 3;
    private List<ComponentRequest> components;

    public static class ComponentRequest {
        private String reportId;
        private String componentType;
        private String title;
        private int columnPosition;
        private int rowPosition;
        private int columnSpan = 1;
        private int rowSpan = 1;
        private String config;
        private int sortOrder;

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
    public List<ComponentRequest> getComponents() { return components; }
    public void setComponents(List<ComponentRequest> components) { this.components = components; }
}
