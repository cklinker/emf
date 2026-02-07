package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ScriptExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptExecutionLogRepository extends JpaRepository<ScriptExecutionLog, String> {

    List<ScriptExecutionLog> findByTenantIdOrderByExecutedAtDesc(String tenantId);

    List<ScriptExecutionLog> findByScriptIdOrderByExecutedAtDesc(String scriptId);
}
