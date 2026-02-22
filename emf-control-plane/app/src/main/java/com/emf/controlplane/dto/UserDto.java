package com.emf.controlplane.dto;

import com.emf.controlplane.entity.User;

import java.time.Instant;

/**
 * DTO for User entity.
 */
public class UserDto {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String status;
    private String locale;
    private String timezone;
    private String managerId;
    private String profileId;
    private Instant lastLoginAt;
    private Integer loginCount;
    private boolean mfaEnabled;
    private Instant createdAt;
    private Instant updatedAt;

    public UserDto() {
    }

    public static UserDto fromEntity(User user) {
        if (user == null) return null;
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.email = user.getEmail();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        dto.username = user.getUsername();
        dto.status = user.getStatus();
        dto.locale = user.getLocale();
        dto.timezone = user.getTimezone();
        dto.managerId = user.getManagerId();
        dto.profileId = user.getProfileId();
        dto.lastLoginAt = user.getLastLoginAt();
        dto.loginCount = user.getLoginCount();
        dto.mfaEnabled = user.isMfaEnabled();
        dto.createdAt = user.getCreatedAt();
        dto.updatedAt = user.getUpdatedAt();
        return dto;
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Integer getLoginCount() { return loginCount; }
    public void setLoginCount(Integer loginCount) { this.loginCount = loginCount; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
