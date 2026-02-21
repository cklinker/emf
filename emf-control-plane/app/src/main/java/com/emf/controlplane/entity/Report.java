package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "report")
public class Report extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_collection_id", nullable = false)
    private Collection primaryCollection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_joins", columnDefinition = "jsonb")
    private String relatedJoins = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns", nullable = false, columnDefinition = "jsonb")
    private String columns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters", columnDefinition = "jsonb")
    private String filters = "[]";

    @Column(name = "filter_logic", length = 500)
    private String filterLogic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_groupings", columnDefinition = "jsonb")
    private String rowGroupings = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_groupings", columnDefinition = "jsonb")
    private String columnGroupings = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sort_order", columnDefinition = "jsonb")
    private String sortOrder = "[]";

    @Column(name = "chart_type", length = 20)
    private String chartType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chart_config", columnDefinition = "jsonb")
    private String chartConfig;

    @Column(name = "scope", length = 20)
    private String scope = "MY_RECORDS";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private ReportFolder folder;

    @Column(name = "access_level", length = 20)
    private String accessLevel = "PRIVATE";

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    public Report() { super(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public Collection getPrimaryCollection() { return primaryCollection; }
    public void setPrimaryCollection(Collection primaryCollection) { this.primaryCollection = primaryCollection; }
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
    public ReportFolder getFolder() { return folder; }
    public void setFolder(ReportFolder folder) { this.folder = folder; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
