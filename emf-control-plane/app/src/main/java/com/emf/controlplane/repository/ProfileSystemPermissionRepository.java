package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ProfileSystemPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileSystemPermissionRepository extends JpaRepository<ProfileSystemPermission, String> {

    List<ProfileSystemPermission> findByProfileId(String profileId);

    List<ProfileSystemPermission> findByProfileIdAndGrantedTrue(String profileId);

    void deleteByProfileId(String profileId);
}
