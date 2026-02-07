package com.emf.controlplane.repository;

import com.emf.controlplane.entity.FieldPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldPermissionRepository extends JpaRepository<FieldPermission, String> {

    List<FieldPermission> findByProfileId(String profileId);

    Optional<FieldPermission> findByProfileIdAndFieldId(String profileId, String fieldId);

    void deleteByProfileId(String profileId);
}
