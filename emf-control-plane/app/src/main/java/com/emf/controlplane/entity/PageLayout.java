package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "page_layout")
public class PageLayout extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "layout_type", length = 20)
    private String layoutType = "DETAIL";

    @Column(name = "is_default")
    private boolean isDefault = false;

    @OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<LayoutSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<LayoutRelatedList> relatedLists = new ArrayList<>();

    public PageLayout() { super(); }

    public PageLayout(String tenantId, Collection collection, String name) {
        super();
        this.tenantId = tenantId;
        this.collection = collection;
        this.name = name;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public List<LayoutSection> getSections() { return sections; }
    public void setSections(List<LayoutSection> sections) { this.sections = sections; }

    public List<LayoutRelatedList> getRelatedLists() { return relatedLists; }
    public void setRelatedLists(List<LayoutRelatedList> relatedLists) { this.relatedLists = relatedLists; }
}
