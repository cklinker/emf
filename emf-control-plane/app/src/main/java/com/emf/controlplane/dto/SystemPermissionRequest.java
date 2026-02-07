package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

public class SystemPermissionRequest {

    @NotBlank(message = "Permission key is required")
    private String permissionKey;

    private boolean granted;

    public SystemPermissionRequest() {}

    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
