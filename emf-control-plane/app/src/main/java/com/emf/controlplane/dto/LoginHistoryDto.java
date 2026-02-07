package com.emf.controlplane.dto;

import com.emf.controlplane.entity.LoginHistory;

import java.time.Instant;

/**
 * DTO for LoginHistory entity.
 */
public class LoginHistoryDto {

    private String id;
    private String userId;
    private Instant loginTime;
    private String sourceIp;
    private String loginType;
    private String status;
    private String userAgent;

    public LoginHistoryDto() {
    }

    public static LoginHistoryDto fromEntity(LoginHistory entity) {
        if (entity == null) return null;
        LoginHistoryDto dto = new LoginHistoryDto();
        dto.id = entity.getId();
        dto.userId = entity.getUserId();
        dto.loginTime = entity.getLoginTime();
        dto.sourceIp = entity.getSourceIp();
        dto.loginType = entity.getLoginType();
        dto.status = entity.getStatus();
        dto.userAgent = entity.getUserAgent();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
}
