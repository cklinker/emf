package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PermsetSystemPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermsetSystemPermissionRepository extends JpaRepository<PermsetSystemPermission, String> {

    List<PermsetSystemPermission> findByPermissionSetId(String permissionSetId);

    Optional<PermsetSystemPermission> findByPermissionSetIdAndPermissionKey(String permissionSetId, String permissionKey);
}
