package com.emf.controlplane.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a domain service in the EMF platform.
 * A service is a deployable runtime (domain microservice) that hosts collections/resources.
 * Each service has its own database and can host multiple collections.
 */
@Entity
@Table(name = "service")
public class Service extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "base_path", length = 100)
    private String basePath = "/api";

    @Column(name = "environment", length = 50)
    private String environment;

    @Column(name = "database_url", length = 500)
    private String databaseUrl;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("name ASC")
    private List<Collection> collections = new ArrayList<>();

    public Service() {
        super();
    }

    public Service(String name, String description) {
        super();
        this.name = name;
        this.displayName = name;
        this.description = description;
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

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public void setDatabaseUrl(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Collection> getCollections() {
        return collections;
    }

    public void setCollections(List<Collection> collections) {
        this.collections = collections;
    }

    public void addCollection(Collection collection) {
        collections.add(collection);
        collection.setService(this);
    }

    public void removeCollection(Collection collection) {
        collections.remove(collection);
        collection.setService(null);
    }

    @Override
    public String toString() {
        return "Service{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", environment='" + environment + '\'' +
                ", active=" + active +
                '}';
    }
}
