package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ProfileFieldPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileFieldPermissionRepository extends JpaRepository<ProfileFieldPermission, String> {

    List<ProfileFieldPermission> findByProfileId(String profileId);

    List<ProfileFieldPermission> findByProfileIdAndCollectionId(String profileId, String collectionId);

    void deleteByProfileId(String profileId);

    void deleteByProfileIdAndCollectionId(String profileId, String collectionId);

    void deleteByFieldId(String fieldId);
}
