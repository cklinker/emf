package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "label_override", length = 200)
    private String labelOverride;

    @Column(name = "help_text_override", length = 500)
    private String helpTextOverride;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visibility_rule", columnDefinition = "jsonb")
    private String visibilityRule;

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

    public String getLabelOverride() { return labelOverride; }
    public void setLabelOverride(String labelOverride) { this.labelOverride = labelOverride; }

    public String getHelpTextOverride() { return helpTextOverride; }
    public void setHelpTextOverride(String helpTextOverride) { this.helpTextOverride = helpTextOverride; }

    public String getVisibilityRule() { return visibilityRule; }
    public void setVisibilityRule(String visibilityRule) { this.visibilityRule = visibilityRule; }
}
