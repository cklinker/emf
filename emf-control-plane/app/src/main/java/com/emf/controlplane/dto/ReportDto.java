package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Report;

import java.time.Instant;

public class ReportDto {

    private String id;
    private String name;
    private String description;
    private String reportType;
    private String primaryCollectionId;
    private String relatedJoins;
    private String columns;
    private String filters;
    private String filterLogic;
    private String rowGroupings;
    private String columnGroupings;
    private String sortOrder;
    private String chartType;
    private String chartConfig;
    private String scope;
    private String folderId;
    private String accessLevel;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static ReportDto fromEntity(Report entity) {
        ReportDto dto = new ReportDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setReportType(entity.getReportType());
        dto.setPrimaryCollectionId(entity.getPrimaryCollection().getId());
        dto.setRelatedJoins(entity.getRelatedJoins());
        dto.setColumns(entity.getColumns());
        dto.setFilters(entity.getFilters());
        dto.setFilterLogic(entity.getFilterLogic());
        dto.setRowGroupings(entity.getRowGroupings());
        dto.setColumnGroupings(entity.getColumnGroupings());
        dto.setSortOrder(entity.getSortOrder());
        dto.setChartType(entity.getChartType());
        dto.setChartConfig(entity.getChartConfig());
        dto.setScope(entity.getScope());
        dto.setFolderId(entity.getFolder() != null ? entity.getFolder().getId() : null);
        dto.setAccessLevel(entity.getAccessLevel());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getPrimaryCollectionId() { return primaryCollectionId; }
    public void setPrimaryCollectionId(String primaryCollectionId) { this.primaryCollectionId = primaryCollectionId; }
    public String getRelatedJoins() { return relatedJoins; }
    public void setRelatedJoins(String relatedJoins) { this.relatedJoins = relatedJoins; }
    public String getColumns() { return columns; }
    public void setColumns(String columns) { this.columns = columns; }
    public String getFilters() { return filters; }
    public void setFilters(String filters) { this.filters = filters; }
    public String getFilterLogic() { return filterLogic; }
    public void setFilterLogic(String filterLogic) { this.filterLogic = filterLogic; }
    public String getRowGroupings() { return rowGroupings; }
    public void setRowGroupings(String rowGroupings) { this.rowGroupings = rowGroupings; }
    public String getColumnGroupings() { return columnGroupings; }
    public void setColumnGroupings(String columnGroupings) { this.columnGroupings = columnGroupings; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }
    public String getChartConfig() { return chartConfig; }
    public void setChartConfig(String chartConfig) { this.chartConfig = chartConfig; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
