package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/**
 * A system permission grant for a profile.
 */
@Entity
@Table(name = "profile_system_permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"profile_id", "permission_name"})
})
public class ProfileSystemPermission {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 36)
    private String profileId;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @Column(name = "granted", nullable = false)
    private boolean granted = false;

    public ProfileSystemPermission() {
        this.id = UUID.randomUUID().toString();
    }

    public ProfileSystemPermission(String profileId, String permissionName, boolean granted) {
        this();
        this.profileId = profileId;
        this.permissionName = permissionName;
        this.granted = granted;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileSystemPermission that = (ProfileSystemPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
