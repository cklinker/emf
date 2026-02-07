package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ConnectedApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectedAppRepository extends JpaRepository<ConnectedApp, String> {

    List<ConnectedApp> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<ConnectedApp> findByClientId(String clientId);
}
