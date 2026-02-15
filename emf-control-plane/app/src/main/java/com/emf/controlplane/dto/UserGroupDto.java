package com.emf.controlplane.dto;

import com.emf.controlplane.entity.GroupMembership;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.entity.UserGroup;

import java.util.List;
import java.util.stream.Collectors;

public class UserGroupDto {

    private String id;
    private String name;
    private String description;
    private String groupType;
    private String source;
    private String oidcGroupName;
    private List<String> memberIds;
    private List<String> childGroupIds;

    public UserGroupDto() {}

    public static UserGroupDto fromEntity(UserGroup entity) {
        UserGroupDto dto = new UserGroupDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setGroupType(entity.getGroupType());
        dto.setSource(entity.getSource());
        dto.setOidcGroupName(entity.getOidcGroupName());
        if (entity.getMembers() != null) {
            dto.setMemberIds(entity.getMembers().stream()
                    .map(User::getId)
                    .collect(Collectors.toList()));
        }
        if (entity.getMemberships() != null) {
            dto.setChildGroupIds(entity.getMemberships().stream()
                    .filter(GroupMembership::isGroupMember)
                    .map(GroupMembership::getMemberId)
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

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getOidcGroupName() { return oidcGroupName; }
    public void setOidcGroupName(String oidcGroupName) { this.oidcGroupName = oidcGroupName; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    public List<String> getChildGroupIds() { return childGroupIds; }
    public void setChildGroupIds(List<String> childGroupIds) { this.childGroupIds = childGroupIds; }
}
