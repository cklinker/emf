package com.emf.controlplane.dto;

import com.emf.controlplane.entity.ListView;

import java.time.Instant;

public class ListViewDto {

    private String id;
    private String collectionId;
    private String name;
    private String createdBy;
    private String visibility;
    private boolean isDefault;
    private String columns;
    private String filterLogic;
    private String filters;
    private String sortField;
    private String sortDirection;
    private int rowLimit;
    private String chartConfig;
    private Instant createdAt;
    private Instant updatedAt;

    public static ListViewDto fromEntity(ListView entity) {
        ListViewDto dto = new ListViewDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollection().getId());
        dto.setName(entity.getName());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setVisibility(entity.getVisibility());
        dto.setDefault(entity.isDefault());
        dto.setColumns(entity.getColumns());
        dto.setFilterLogic(entity.getFilterLogic());
        dto.setFilters(entity.getFilters());
        dto.setSortField(entity.getSortField());
        dto.setSortDirection(entity.getSortDirection());
        dto.setRowLimit(entity.getRowLimit());
        dto.setChartConfig(entity.getChartConfig());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public String getColumns() { return columns; }
    public void setColumns(String columns) { this.columns = columns; }
    public String getFilterLogic() { return filterLogic; }
    public void setFilterLogic(String filterLogic) { this.filterLogic = filterLogic; }
    public String getFilters() { return filters; }
    public void setFilters(String filters) { this.filters = filters; }
    public String getSortField() { return sortField; }
    public void setSortField(String sortField) { this.sortField = sortField; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
    public int getRowLimit() { return rowLimit; }
    public void setRowLimit(int rowLimit) { this.rowLimit = rowLimit; }
    public String getChartConfig() { return chartConfig; }
    public void setChartConfig(String chartConfig) { this.chartConfig = chartConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
