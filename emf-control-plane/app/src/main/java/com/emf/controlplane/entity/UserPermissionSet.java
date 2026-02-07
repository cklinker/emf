package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Junction entity linking a user to a permission set.
 * Uses a composite primary key of (userId, permissionSetId).
 */
@Entity
@Table(name = "user_permission_set")
@IdClass(UserPermissionSet.UserPermissionSetId.class)
@EntityListeners(AuditingEntityListener.class)
public class UserPermissionSet {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Id
    @Column(name = "permission_set_id", nullable = false, length = 36)
    private String permissionSetId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserPermissionSet() {}

    public UserPermissionSet(String userId, String permissionSetId) {
        this.userId = userId;
        this.permissionSetId = permissionSetId;
    }

    // Getters and setters

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPermissionSetId() { return permissionSetId; }
    public void setPermissionSetId(String permissionSetId) { this.permissionSetId = permissionSetId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Composite primary key for UserPermissionSet.
     */
    public static class UserPermissionSetId implements Serializable {
        private String userId;
        private String permissionSetId;

        public UserPermissionSetId() {}

        public UserPermissionSetId(String userId, String permissionSetId) {
            this.userId = userId;
            this.permissionSetId = permissionSetId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserPermissionSetId that = (UserPermissionSetId) o;
            return Objects.equals(userId, that.userId) &&
                    Objects.equals(permissionSetId, that.permissionSetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, permissionSetId);
        }
    }
}
