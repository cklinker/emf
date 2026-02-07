package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collection definition in the EMF platform.
 * A collection is a logical grouping of data entities with defined fields and operations.
 * Each collection belongs to a domain service.
 */
@Entity
@Table(name = "collection")
public class Collection extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "path", length = 255)
    private String path;

    @Column(name = "storage_mode", length = 50)
    private String storageMode = "PHYSICAL_TABLE";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Field> fields = new ArrayList<>();

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version DESC")
    private List<CollectionVersion> versions = new ArrayList<>();

    public Collection() {
        super();
    }

    public Collection(Service service, String name, String description) {
        super();
        this.service = service;
        this.name = name;
        this.displayName = name;
        this.description = description;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(String storageMode) {
        this.storageMode = storageMode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public void addField(Field field) {
        fields.add(field);
        field.setCollection(this);
    }

    public void removeField(Field field) {
        fields.remove(field);
        field.setCollection(null);
    }

    public List<CollectionVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<CollectionVersion> versions) {
        this.versions = versions;
    }

    public void addVersion(CollectionVersion version) {
        versions.add(version);
        version.setCollection(this);
    }

    @Override
    public String toString() {
        return "Collection{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                ", currentVersion=" + currentVersion +
                '}';
    }
}
