package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, String> {

    List<UserGroup> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<UserGroup> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);

    @Query("SELECT g FROM UserGroup g JOIN g.members m WHERE m.id = :userId")
    List<UserGroup> findGroupsByUserId(@Param("userId") String userId);
}
