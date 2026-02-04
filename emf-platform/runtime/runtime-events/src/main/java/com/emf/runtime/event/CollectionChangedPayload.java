package com.emf.runtime.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Payload for collection changed events.
 * Contains the full collection entity data for consumers.
 * 
 * This is a shared event payload used across all EMF services.
 */
public class CollectionChangedPayload {

    private String id;
    private String serviceId;
    private String serviceName;
    private String name;
    private String displayName;
    private String description;
    private String storageMode;
    private boolean active;
    private Integer currentVersion;
    private List<FieldPayload> fields;
    private Instant createdAt;
    private Instant updatedAt;
    private ChangeType changeType;

    /**
     * Default constructor for deserialization.
     */
    public CollectionChangedPayload() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    public List<FieldPayload> getFields() {
        return fields;
    }

    public void setFields(List<FieldPayload> fields) {
        this.fields = fields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollectionChangedPayload that = (CollectionChangedPayload) o;
        return active == that.active &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(currentVersion, that.currentVersion) &&
                changeType == that.changeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, active, currentVersion, changeType);
    }

    @Override
    public String toString() {
        return "CollectionChangedPayload{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", changeType=" + changeType +
                ", currentVersion=" + currentVersion +
                ", active=" + active +
                '}';
    }

    /**
     * Nested class for field data in the payload.
     */
    public static class FieldPayload {
        private String id;
        private String name;
        private String type;
        private boolean required;
        private String description;
        private String constraints;

        public FieldPayload() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getConstraints() {
            return constraints;
        }

        public void setConstraints(String constraints) {
            this.constraints = constraints;
        }
    }
}
