package com.emf.controlplane.dto;

import com.emf.controlplane.entity.User;
import com.emf.controlplane.entity.UserGroup;

import java.util.List;
import java.util.stream.Collectors;

public class UserGroupDto {

    private String id;
    private String name;
    private String description;
    private String groupType;
    private List<String> memberIds;

    public UserGroupDto() {}

    public static UserGroupDto fromEntity(UserGroup entity) {
        UserGroupDto dto = new UserGroupDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setGroupType(entity.getGroupType());
        if (entity.getMembers() != null) {
            dto.setMemberIds(entity.getMembers().stream()
                    .map(User::getId)
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

    public String getGroupType() { return groupType; }
    public void setGroupType(String groupType) { this.groupType = groupType; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }
}
