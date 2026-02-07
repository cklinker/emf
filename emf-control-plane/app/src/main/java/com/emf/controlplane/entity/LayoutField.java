package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "layout_field")
public class LayoutField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private LayoutSection section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @Column(name = "column_number")
    private int columnNumber = 1;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_required_on_layout")
    private boolean requiredOnLayout = false;

    @Column(name = "is_read_only_on_layout")
    private boolean readOnlyOnLayout = false;

    public LayoutField() { super(); }

    public LayoutSection getSection() { return section; }
    public void setSection(LayoutSection section) { this.section = section; }

    public Field getField() { return field; }
    public void setField(Field field) { this.field = field; }

    public int getColumnNumber() { return columnNumber; }
    public void setColumnNumber(int columnNumber) { this.columnNumber = columnNumber; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isRequiredOnLayout() { return requiredOnLayout; }
    public void setRequiredOnLayout(boolean requiredOnLayout) { this.requiredOnLayout = requiredOnLayout; }

    public boolean isReadOnlyOnLayout() { return readOnlyOnLayout; }
    public void setReadOnlyOnLayout(boolean readOnlyOnLayout) { this.readOnlyOnLayout = readOnlyOnLayout; }
}
