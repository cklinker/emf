package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "layout_related_list")
public class LayoutRelatedList extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_id", nullable = false)
    private PageLayout layout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_collection_id", nullable = false)
    private Collection relatedCollection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relationship_field_id", nullable = false)
    private Field relationshipField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "display_columns", nullable = false, columnDefinition = "jsonb")
    private String displayColumns;

    @Column(name = "sort_field", length = 100)
    private String sortField;

    @Column(name = "sort_direction", length = 4)
    private String sortDirection = "DESC";

    @Column(name = "row_limit")
    private int rowLimit = 10;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public LayoutRelatedList() { super(); }

    public PageLayout getLayout() { return layout; }
    public void setLayout(PageLayout layout) { this.layout = layout; }

    public Collection getRelatedCollection() { return relatedCollection; }
    public void setRelatedCollection(Collection relatedCollection) { this.relatedCollection = relatedCollection; }

    public Field getRelationshipField() { return relationshipField; }
    public void setRelationshipField(Field relationshipField) { this.relationshipField = relationshipField; }

    public String getDisplayColumns() { return displayColumns; }
    public void setDisplayColumns(String displayColumns) { this.displayColumns = displayColumns; }

    public String getSortField() { return sortField; }
    public void setSortField(String sortField) { this.sortField = sortField; }

    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }

    public int getRowLimit() { return rowLimit; }
    public void setRowLimit(int rowLimit) { this.rowLimit = rowLimit; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
