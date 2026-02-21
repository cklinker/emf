package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "record_type")
public class RecordType extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @OneToMany(mappedBy = "recordType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecordTypePicklist> picklistOverrides = new ArrayList<>();

    public RecordType() {
        super();
    }

    public RecordType(String tenantId, Collection collection, String name) {
        super(tenantId);
        this.collection = collection;
        this.name = name;
    }

    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public List<RecordTypePicklist> getPicklistOverrides() { return picklistOverrides; }
    public void setPicklistOverrides(List<RecordTypePicklist> picklistOverrides) { this.picklistOverrides = picklistOverrides; }

    @Override
    public String toString() {
        return "RecordType{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                ", isDefault=" + isDefault +
                '}';
    }
}
