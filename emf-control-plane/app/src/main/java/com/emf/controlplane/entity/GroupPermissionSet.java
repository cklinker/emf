package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Join entity for group-to-permission-set assignments (many-to-many).
 * All members of the group (including nested group members) inherit these permission sets.
 */
@Entity
@Table(name = "group_permission_set", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"group_id", "permission_set_id"})
})
public class GroupPermissionSet {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "group_id", nullable = false, length = 36)
    private String groupId;

    @Column(name = "permission_set_id", nullable = false, length = 36)
    private String permissionSetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupPermissionSet() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public GroupPermissionSet(String groupId, String permissionSetId) {
        this();
        this.groupId = groupId;
        this.permissionSetId = permissionSetId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getPermissionSetId() { return permissionSetId; }
    public void setPermissionSetId(String permissionSetId) { this.permissionSetId = permissionSetId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupPermissionSet that = (GroupPermissionSet) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
