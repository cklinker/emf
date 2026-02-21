package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UserPermissionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPermissionSetRepository extends JpaRepository<UserPermissionSet, String> {

    List<UserPermissionSet> findByUserId(String userId);

    List<UserPermissionSet> findByPermissionSetId(String permissionSetId);

    boolean existsByUserIdAndPermissionSetId(String userId, String permissionSetId);

    void deleteByUserIdAndPermissionSetId(String userId, String permissionSetId);

    void deleteByUserId(String userId);

    @Query("SELECT ups.permissionSetId FROM UserPermissionSet ups WHERE ups.userId = :userId")
    List<String> findPermissionSetIdsByUserId(@Param("userId") String userId);
}
