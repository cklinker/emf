package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Role;

import java.util.List;
import java.util.stream.Collectors;

public class RoleHierarchyDto {

    private String id;
    private String name;
    private String description;
    private String parentRoleId;
    private Integer hierarchyLevel;
    private List<RoleHierarchyDto> children;

    public RoleHierarchyDto() {}

    public static RoleHierarchyDto fromEntity(Role role) {
        RoleHierarchyDto dto = new RoleHierarchyDto();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setParentRoleId(role.getParentRole() != null ? role.getParentRole().getId() : null);
        dto.setHierarchyLevel(role.getHierarchyLevel());
        if (role.getChildRoles() != null) {
            dto.setChildren(role.getChildRoles().stream()
                    .map(RoleHierarchyDto::fromEntity)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getParentRoleId() { return parentRoleId; }
    public void setParentRoleId(String parentRoleId) { this.parentRoleId = parentRoleId; }

    public Integer getHierarchyLevel() { return hierarchyLevel; }
    public void setHierarchyLevel(Integer hierarchyLevel) { this.hierarchyLevel = hierarchyLevel; }

    public List<RoleHierarchyDto> getChildren() { return children; }
    public void setChildren(List<RoleHierarchyDto> children) { this.children = children; }
}
