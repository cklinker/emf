package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PermsetObjectPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermsetObjectPermissionRepository extends JpaRepository<PermsetObjectPermission, String> {

    List<PermsetObjectPermission> findByPermissionSetId(String permissionSetId);

    void deleteByPermissionSetId(String permissionSetId);

    void deleteByCollectionId(String collectionId);
}
