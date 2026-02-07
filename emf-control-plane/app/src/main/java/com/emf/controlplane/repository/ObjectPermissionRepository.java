package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ObjectPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObjectPermissionRepository extends JpaRepository<ObjectPermission, String> {

    List<ObjectPermission> findByProfileId(String profileId);

    Optional<ObjectPermission> findByProfileIdAndCollectionId(String profileId, String collectionId);

    void deleteByProfileIdAndCollectionId(String profileId, String collectionId);
}
