package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Join entity for user-to-permission-set assignments (many-to-many).
 */
@Entity
@Table(name = "user_permission_set", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "permission_set_id"})
})
public class UserPermissionSet {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "permission_set_id", nullable = false, length = 36)
    private String permissionSetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserPermissionSet() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public UserPermissionSet(String userId, String permissionSetId) {
        this();
        this.userId = userId;
        this.permissionSetId = permissionSetId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPermissionSetId() { return permissionSetId; }
    public void setPermissionSetId(String permissionSetId) { this.permissionSetId = permissionSetId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPermissionSet that = (UserPermissionSet) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
