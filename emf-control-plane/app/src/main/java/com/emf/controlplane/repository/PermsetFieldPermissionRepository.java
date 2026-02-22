package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PermsetFieldPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermsetFieldPermissionRepository extends JpaRepository<PermsetFieldPermission, String> {

    List<PermsetFieldPermission> findByPermissionSetId(String permissionSetId);

    List<PermsetFieldPermission> findByPermissionSetIdAndCollectionId(String permissionSetId, String collectionId);

    void deleteByPermissionSetId(String permissionSetId);

    void deleteByPermissionSetIdAndCollectionId(String permissionSetId, String collectionId);

    void deleteByFieldId(String fieldId);
}
