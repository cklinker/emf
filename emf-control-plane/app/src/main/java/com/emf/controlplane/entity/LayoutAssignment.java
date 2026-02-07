package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "layout_assignment")
public class LayoutAssignment extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "profile_id", nullable = false, length = 36)
    private String profileId;

    @Column(name = "record_type_id", length = 36)
    private String recordTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_id", nullable = false)
    private PageLayout layout;

    public LayoutAssignment() { super(); }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getRecordTypeId() { return recordTypeId; }
    public void setRecordTypeId(String recordTypeId) { this.recordTypeId = recordTypeId; }

    public PageLayout getLayout() { return layout; }
    public void setLayout(PageLayout layout) { this.layout = layout; }
}
