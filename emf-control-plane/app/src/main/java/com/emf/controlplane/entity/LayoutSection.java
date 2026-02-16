package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "layout_section")
public class LayoutSection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_id", nullable = false)
    private PageLayout layout;

    @Column(name = "heading", length = 200)
    private String heading;

    @Column(name = "columns")
    private int columns = 2;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "collapsed")
    private boolean collapsed = false;

    @Column(name = "style", length = 20)
    private String style = "DEFAULT";

    @Column(name = "section_type", length = 30)
    private String sectionType = "STANDARD";

    @Column(name = "tab_group", length = 100)
    private String tabGroup;

    @Column(name = "tab_label", length = 200)
    private String tabLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visibility_rule", columnDefinition = "jsonb")
    private String visibilityRule;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<LayoutField> fields = new ArrayList<>();

    public LayoutSection() { super(); }

    public PageLayout getLayout() { return layout; }
    public void setLayout(PageLayout layout) { this.layout = layout; }

    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isCollapsed() { return collapsed; }
    public void setCollapsed(boolean collapsed) { this.collapsed = collapsed; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }

    public String getTabGroup() { return tabGroup; }
    public void setTabGroup(String tabGroup) { this.tabGroup = tabGroup; }

    public String getTabLabel() { return tabLabel; }
    public void setTabLabel(String tabLabel) { this.tabLabel = tabLabel; }

    public String getVisibilityRule() { return visibilityRule; }
    public void setVisibilityRule(String visibilityRule) { this.visibilityRule = visibilityRule; }

    public List<LayoutField> getFields() { return fields; }
    public void setFields(List<LayoutField> fields) { this.fields = fields; }
}
