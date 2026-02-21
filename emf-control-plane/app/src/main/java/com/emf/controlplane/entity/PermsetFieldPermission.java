package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/**
 * Field-level visibility permission for a permission set.
 */
@Entity
@Table(name = "permset_field_permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"permission_set_id", "field_id"})
})
public class PermsetFieldPermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "permission_set_id", nullable = false, length = 36)
    private String permissionSetId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "field_id", nullable = false, length = 36)
    private String fieldId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private FieldVisibility visibility = FieldVisibility.VISIBLE;

    public PermsetFieldPermission() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPermissionSetId() { return permissionSetId; }
    public void setPermissionSetId(String permissionSetId) { this.permissionSetId = permissionSetId; }

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
        PermsetFieldPermission that = (PermsetFieldPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
