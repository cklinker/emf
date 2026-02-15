package com.emf.controlplane.repository;

import com.emf.controlplane.entity.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for group membership operations.
 * Supports both USER and GROUP member types for nestable groups.
 */
@Repository
public interface GroupMembershipRepository extends JpaRepository<GroupMembership, String> {

    /**
     * Find all memberships for a specific group.
     */
    List<GroupMembership> findByGroupId(String groupId);

    /**
     * Find all groups a user directly belongs to.
     */
    @Query("SELECT gm FROM GroupMembership gm WHERE gm.memberType = 'USER' AND gm.memberId = :userId")
    List<GroupMembership> findDirectGroupsForUser(@Param("userId") String userId);

    /**
     * Find all groups that contain a specific child group.
     */
    @Query("SELECT gm FROM GroupMembership gm WHERE gm.memberType = 'GROUP' AND gm.memberId = :groupId")
    List<GroupMembership> findParentGroupsForGroup(@Param("groupId") String groupId);

    /**
     * Find a specific membership entry.
     */
    Optional<GroupMembership> findByGroupIdAndMemberTypeAndMemberId(
            String groupId, String memberType, String memberId);

    /**
     * Check if a membership exists.
     */
    boolean existsByGroupIdAndMemberTypeAndMemberId(
            String groupId, String memberType, String memberId);

    /**
     * Delete a specific membership.
     */
    void deleteByGroupIdAndMemberTypeAndMemberId(
            String groupId, String memberType, String memberId);

    /**
     * Delete all memberships for a member (used when removing a user from all groups).
     */
    void deleteByMemberTypeAndMemberId(String memberType, String memberId);

    /**
     * Find all user member IDs for a specific group (direct members only).
     */
    @Query("SELECT gm.memberId FROM GroupMembership gm WHERE gm.groupId = :groupId AND gm.memberType = 'USER'")
    List<String> findUserMemberIdsByGroupId(@Param("groupId") String groupId);

    /**
     * Find all child group IDs for a specific group (direct children only).
     */
    @Query("SELECT gm.memberId FROM GroupMembership gm WHERE gm.groupId = :groupId AND gm.memberType = 'GROUP'")
    List<String> findChildGroupIdsByGroupId(@Param("groupId") String groupId);

    /**
     * Find all group IDs a user directly belongs to.
     */
    @Query("SELECT gm.groupId FROM GroupMembership gm WHERE gm.memberType = 'USER' AND gm.memberId = :userId")
    List<String> findGroupIdsByUserId(@Param("userId") String userId);
}
