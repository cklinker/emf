package com.emf.controlplane.entity;

import jakarta.persistence.*;
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

    public List<LayoutField> getFields() { return fields; }
    public void setFields(List<LayoutField> fields) { this.fields = fields; }
}
