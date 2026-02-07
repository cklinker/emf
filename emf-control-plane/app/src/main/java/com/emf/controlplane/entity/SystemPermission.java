package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Represents a system-wide permission assigned to a profile.
 * Controls access to platform-level operations like user management,
 * application customization, data export, etc.
 */
@Entity
@Table(name = "system_permission")
public class SystemPermission extends BaseEntity {

    public static final String MANAGE_USERS = "MANAGE_USERS";
    public static final String CUSTOMIZE_APPLICATION = "CUSTOMIZE_APPLICATION";
    public static final String MANAGE_SHARING = "MANAGE_SHARING";
    public static final String MANAGE_WORKFLOWS = "MANAGE_WORKFLOWS";
    public static final String MANAGE_REPORTS = "MANAGE_REPORTS";
    public static final String API_ACCESS = "API_ACCESS";
    public static final String MANAGE_INTEGRATIONS = "MANAGE_INTEGRATIONS";
    public static final String MANAGE_DATA = "MANAGE_DATA";
    public static final String VIEW_SETUP = "VIEW_SETUP";
    public static final String MANAGE_SANDBOX = "MANAGE_SANDBOX";
    public static final String VIEW_ALL_DATA = "VIEW_ALL_DATA";
    public static final String MODIFY_ALL_DATA = "MODIFY_ALL_DATA";

    public static final java.util.Set<String> VALID_KEYS = java.util.Set.of(
            MANAGE_USERS, CUSTOMIZE_APPLICATION, MANAGE_SHARING,
            MANAGE_WORKFLOWS, MANAGE_REPORTS, API_ACCESS,
            MANAGE_INTEGRATIONS, MANAGE_DATA, VIEW_SETUP,
            MANAGE_SANDBOX, VIEW_ALL_DATA, MODIFY_ALL_DATA
    );

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(name = "granted")
    private boolean granted = false;

    public SystemPermission() {
        super();
    }

    // Getters and setters

    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
