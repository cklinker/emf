package com.emf.controlplane.repository;

import com.emf.controlplane.entity.SystemPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemPermissionRepository extends JpaRepository<SystemPermission, String> {

    List<SystemPermission> findByProfileId(String profileId);

    Optional<SystemPermission> findByProfileIdAndPermissionKey(String profileId, String permissionKey);

    void deleteByProfileId(String profileId);
}
