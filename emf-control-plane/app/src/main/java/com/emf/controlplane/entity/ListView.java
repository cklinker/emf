package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "list_view")
public class ListView extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "visibility", length = 20)
    private String visibility = "PRIVATE";

    @Column(name = "is_default")
    private boolean isDefault = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns", nullable = false, columnDefinition = "jsonb")
    private String columns;

    @Column(name = "filter_logic", length = 500)
    private String filterLogic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters", nullable = false, columnDefinition = "jsonb")
    private String filters = "[]";

    @Column(name = "sort_field", length = 100)
    private String sortField;

    @Column(name = "sort_direction", length = 4)
    private String sortDirection = "ASC";

    @Column(name = "row_limit")
    private int rowLimit = 50;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chart_config", columnDefinition = "jsonb")
    private String chartConfig;

    public ListView() { super(); }

    public ListView(String tenantId, Collection collection, String name, String createdBy) {
        super(tenantId);
        this.collection = collection;
        this.name = name;
        this.createdBy = createdBy;
    }

    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }
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
}
