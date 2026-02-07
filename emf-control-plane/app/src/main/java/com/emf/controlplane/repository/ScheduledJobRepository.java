package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, String> {

    List<ScheduledJob> findByTenantIdOrderByNameAsc(String tenantId);

    @Query("SELECT j FROM ScheduledJob j WHERE j.active = true AND j.nextRunAt <= :now")
    List<ScheduledJob> findDueJobs(@Param("now") Instant now);
}
