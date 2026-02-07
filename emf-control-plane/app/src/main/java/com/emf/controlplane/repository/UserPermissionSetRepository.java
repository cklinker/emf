package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UserPermissionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPermissionSetRepository extends JpaRepository<UserPermissionSet, UserPermissionSet.UserPermissionSetId> {

    List<UserPermissionSet> findByUserId(String userId);

    List<UserPermissionSet> findByPermissionSetId(String permissionSetId);

    boolean existsByUserIdAndPermissionSetId(String userId, String permissionSetId);

    void deleteByUserIdAndPermissionSetId(String userId, String permissionSetId);

    long countByPermissionSetId(String permissionSetId);
}
