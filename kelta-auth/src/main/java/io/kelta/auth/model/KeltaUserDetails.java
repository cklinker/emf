package io.kelta.auth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class KeltaUserDetails implements UserDetails {

    private final String id;
    private final String email;
    private final String tenantId;
    private final String profileId;
    private final String profileName;
    private final String displayName;
    private final String passwordHash;
    private final boolean active;
    private final boolean locked;
    private final boolean forceChangePassword;

    public KeltaUserDetails(String id, String email, String tenantId, String profileId,
                            String profileName, String displayName, String passwordHash,
                            boolean active, boolean locked, boolean forceChangePassword) {
        this.id = id;
        this.email = email;
        this.tenantId = tenantId;
        this.profileId = profileId;
        this.profileName = profileName;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.active = active;
        this.locked = locked;
        this.forceChangePassword = forceChangePassword;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getTenantId() { return tenantId; }
    public String getProfileId() { return profileId; }
    public String getProfileName() { return profileName; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public boolean isLocked() { return locked; }
    public boolean isForceChangePassword() { return forceChangePassword; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !forceChangePassword;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
