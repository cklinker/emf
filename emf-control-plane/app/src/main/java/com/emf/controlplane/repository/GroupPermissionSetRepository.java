package com.emf.controlplane.repository;

import com.emf.controlplane.entity.GroupPermissionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupPermissionSetRepository extends JpaRepository<GroupPermissionSet, String> {

    List<GroupPermissionSet> findByGroupId(String groupId);

    List<GroupPermissionSet> findByPermissionSetId(String permissionSetId);

    boolean existsByGroupIdAndPermissionSetId(String groupId, String permissionSetId);

    void deleteByGroupIdAndPermissionSetId(String groupId, String permissionSetId);

    void deleteByGroupId(String groupId);

    @Query("SELECT gps.permissionSetId FROM GroupPermissionSet gps WHERE gps.groupId IN :groupIds")
    List<String> findPermissionSetIdsByGroupIds(@Param("groupIds") List<String> groupIds);
}
