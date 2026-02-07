package com.emf.controlplane.dto;

import com.emf.controlplane.entity.PermsetSystemPermission;
import com.emf.controlplane.entity.SystemPermission;

public class SystemPermissionDto {

    private String id;
    private String permissionKey;
    private boolean granted;

    public SystemPermissionDto() {}

    public static SystemPermissionDto fromEntity(SystemPermission entity) {
        if (entity == null) return null;
        SystemPermissionDto dto = new SystemPermissionDto();
        dto.id = entity.getId();
        dto.permissionKey = entity.getPermissionKey();
        dto.granted = entity.isGranted();
        return dto;
    }

    public static SystemPermissionDto fromPermsetEntity(PermsetSystemPermission entity) {
        if (entity == null) return null;
        SystemPermissionDto dto = new SystemPermissionDto();
        dto.id = entity.getId();
        dto.permissionKey = entity.getPermissionKey();
        dto.granted = entity.isGranted();
        return dto;
    }

    // Getters and setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPermissionKey() { return permissionKey; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
