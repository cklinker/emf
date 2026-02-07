package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Records each authentication attempt for audit and security purposes.
 */
@Entity
@Table(name = "login_history")
@EntityListeners(AuditingEntityListener.class)
public class LoginHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

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
        this.id = UUID.randomUUID().toString();
        this.loginTime = Instant.now();
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

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
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LoginHistory{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
