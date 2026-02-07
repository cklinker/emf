package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptRepository extends JpaRepository<Script, String> {

    List<Script> findByTenantIdOrderByNameAsc(String tenantId);

    List<Script> findByTenantIdAndActiveTrue(String tenantId);
}
