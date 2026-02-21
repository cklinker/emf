package com.emf.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Records each authentication attempt for audit and security purposes.
 */
@Entity
@Table(name = "login_history")
public class LoginHistory extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "login_time", nullable = false)
    private Instant loginTime;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "login_type", nullable = false, length = 20)
    private String loginType = "UI";

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public LoginHistory() {
        super();
        this.loginTime = Instant.now();
    }

    // Getters and setters

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getLoginTime() { return loginTime; }
    public void setLoginTime(Instant loginTime) { this.loginTime = loginTime; }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoginHistory that = (LoginHistory) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "LoginHistory{" +
                "id='" + getId() + '\'' +
                ", userId='" + userId + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
