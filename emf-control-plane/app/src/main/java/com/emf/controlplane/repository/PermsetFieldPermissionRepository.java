package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PermsetFieldPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermsetFieldPermissionRepository extends JpaRepository<PermsetFieldPermission, String> {

    List<PermsetFieldPermission> findByPermissionSetId(String permissionSetId);

    Optional<PermsetFieldPermission> findByPermissionSetIdAndFieldId(String permissionSetId, String fieldId);
}
