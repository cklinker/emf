package com.emf.controlplane.repository;

import com.emf.controlplane.entity.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, String> {

    List<JobExecutionLog> findByJobIdOrderByStartedAtDesc(String jobId);
}
