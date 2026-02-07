package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dashboard_component")
public class DashboardComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id", nullable = false)
    private UserDashboard dashboard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "component_type", nullable = false, length = 20)
    private String componentType;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "column_position", nullable = false)
    private int columnPosition;

    @Column(name = "row_position", nullable = false)
    private int rowPosition;

    @Column(name = "column_span")
    private int columnSpan = 1;

    @Column(name = "row_span")
    private int rowSpan = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private String config = "{}";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public DashboardComponent() { super(); }

    public UserDashboard getDashboard() { return dashboard; }
    public void setDashboard(UserDashboard dashboard) { this.dashboard = dashboard; }
    public Report getReport() { return report; }
    public void setReport(Report report) { this.report = report; }
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
