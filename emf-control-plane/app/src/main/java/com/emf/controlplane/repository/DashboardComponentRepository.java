package com.emf.controlplane.repository;

import com.emf.controlplane.entity.DashboardComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashboardComponentRepository extends JpaRepository<DashboardComponent, String> {

    List<DashboardComponent> findByDashboardIdOrderBySortOrderAsc(String dashboardId);

    void deleteByDashboardId(String dashboardId);
}
