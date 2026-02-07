package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, String> {

    List<Webhook> findByTenantIdOrderByNameAsc(String tenantId);

    List<Webhook> findByTenantIdAndActiveTrue(String tenantId);
}
