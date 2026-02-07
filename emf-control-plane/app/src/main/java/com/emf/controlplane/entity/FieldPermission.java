package com.emf.controlplane.entity;

import jakarta.persistence.*;

/**
 * Represents field-level visibility permissions assigned to a profile.
 * Controls whether a field is visible, read-only, or hidden for a user.
 */
@Entity
@Table(name = "field_permission")
public class FieldPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "field_id", nullable = false, length = 36)
    private String fieldId;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "VISIBLE";

    public FieldPermission() {
        super();
    }

    // Getters and setters

    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
