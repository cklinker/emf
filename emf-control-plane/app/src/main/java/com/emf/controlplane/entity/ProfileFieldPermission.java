package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/**
 * Field-level visibility permission for a profile.
 */
@Entity
@Table(name = "profile_field_permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"profile_id", "field_id"})
})
public class ProfileFieldPermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 36)
    private String profileId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "field_id", nullable = false, length = 36)
    private String fieldId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private FieldVisibility visibility = FieldVisibility.VISIBLE;

    public ProfileFieldPermission() {
        this.id = UUID.randomUUID().toString();
    }

    public ProfileFieldPermission(String profileId, String collectionId, String fieldId, FieldVisibility visibility) {
        this();
        this.profileId = profileId;
        this.collectionId = collectionId;
        this.fieldId = fieldId;
        this.visibility = visibility;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public FieldVisibility getVisibility() { return visibility; }
    public void setVisibility(FieldVisibility visibility) { this.visibility = visibility; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileFieldPermission that = (ProfileFieldPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
