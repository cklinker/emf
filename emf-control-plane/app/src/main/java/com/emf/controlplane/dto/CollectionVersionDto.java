package com.emf.controlplane.dto;

import com.emf.controlplane.entity.CollectionVersion;

import java.time.Instant;

/**
 * DTO for CollectionVersion entity.
 * Represents an immutable snapshot of a collection's schema at a specific version.
 */
public class CollectionVersionDto {

    private String id;
    private String collectionId;
    private String collectionName;
    private Integer version;
    private String schema;
    private Instant createdAt;

    public CollectionVersionDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Converts a CollectionVersion entity to a DTO.
     */
    public static CollectionVersionDto fromEntity(CollectionVersion entity) {
        CollectionVersionDto dto = new CollectionVersionDto();
        dto.setId(entity.getId());
        dto.setCollectionId(entity.getCollection().getId());
        dto.setCollectionName(entity.getCollection().getName());
        dto.setVersion(entity.getVersion());
        dto.setSchema(entity.getSchema());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
