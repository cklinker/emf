package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Represents a platform user within a tenant.
 * Users are provisioned via JIT (OIDC) or manual creation.
 */
@Entity
@Table(name = "platform_user")
public class User extends TenantScopedEntity {

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "locale", length = 10)
    private String locale = "en_US";

    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Column(name = "profile_id", length = 36)
    private String profileId;

    @Column(name = "manager_id", length = 36)
    private String managerId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "login_count")
    private Integer loginCount = 0;

    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private String settings = "{}";

    public User() {
        super();
    }

    public User(String email, String firstName, String lastName) {
        super();
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return firstName != null ? firstName : (lastName != null ? lastName : email);
    }

    // Getters and setters

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Integer getLoginCount() { return loginCount; }
    public void setLoginCount(Integer loginCount) { this.loginCount = loginCount; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }

    @Override
    public String toString() {
        return "User{" +
                "id='" + getId() + '\'' +
                ", email='" + email + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
