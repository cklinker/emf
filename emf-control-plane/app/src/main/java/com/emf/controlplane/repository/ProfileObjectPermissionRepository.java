package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ProfileObjectPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileObjectPermissionRepository extends JpaRepository<ProfileObjectPermission, String> {

    List<ProfileObjectPermission> findByProfileId(String profileId);

    Optional<ProfileObjectPermission> findByProfileIdAndCollectionId(String profileId, String collectionId);

    void deleteByProfileId(String profileId);

    void deleteByCollectionId(String collectionId);
}
