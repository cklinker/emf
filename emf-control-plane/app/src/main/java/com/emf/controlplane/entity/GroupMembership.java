package com.emf.controlplane.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a membership entry in a group.
 * Supports both USER and GROUP member types, enabling nested group hierarchies.
 */
@Entity
@Table(name = "group_membership",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_group_membership",
                columnNames = {"group_id", "member_type", "member_id"}))
public class GroupMembership {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "group_id", nullable = false, length = 36)
    private String groupId;

    @Column(name = "member_type", nullable = false, length = 10)
    private String memberType;

    @Column(name = "member_id", nullable = false, length = 36)
    private String memberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupMembership() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public GroupMembership(String groupId, String memberType, String memberId) {
        this();
        this.groupId = groupId;
        this.memberType = memberType;
        this.memberId = memberId;
    }

    // Constants for member types
    public static final String MEMBER_TYPE_USER = "USER";
    public static final String MEMBER_TYPE_GROUP = "GROUP";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getMemberType() { return memberType; }
    public void setMemberType(String memberType) { this.memberType = memberType; }

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isUserMember() {
        return MEMBER_TYPE_USER.equals(memberType);
    }

    public boolean isGroupMember() {
        return MEMBER_TYPE_GROUP.equals(memberType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupMembership that = (GroupMembership) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
