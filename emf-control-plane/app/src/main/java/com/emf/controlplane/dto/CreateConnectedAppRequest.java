package com.emf.controlplane.dto;

public class CreateConnectedAppRequest {

    private String name;
    private String description;
    private String redirectUris;
    private String scopes;
    private String ipRestrictions;
    private Integer rateLimitPerHour;
    private Boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRedirectUris() { return redirectUris; }
    public void setRedirectUris(String redirectUris) { this.redirectUris = redirectUris; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public String getIpRestrictions() { return ipRestrictions; }
    public void setIpRestrictions(String ipRestrictions) { this.ipRestrictions = ipRestrictions; }
    public Integer getRateLimitPerHour() { return rateLimitPerHour; }
    public void setRateLimitPerHour(Integer rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
