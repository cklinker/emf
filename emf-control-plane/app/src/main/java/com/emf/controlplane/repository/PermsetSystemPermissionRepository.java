package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PermsetSystemPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermsetSystemPermissionRepository extends JpaRepository<PermsetSystemPermission, String> {

    List<PermsetSystemPermission> findByPermissionSetId(String permissionSetId);

    List<PermsetSystemPermission> findByPermissionSetIdAndGrantedTrue(String permissionSetId);

    void deleteByPermissionSetId(String permissionSetId);
}
