package com.emf.controlplane.repository;

import com.emf.controlplane.entity.OidcProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for OidcProvider entity operations.
 */
@Repository
public interface OidcProviderRepository extends JpaRepository<OidcProvider, String> {

    // ---- Tenant-scoped methods ----

    List<OidcProvider> findByTenantIdAndActiveTrue(String tenantId);

    List<OidcProvider> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    Optional<OidcProvider> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);

    Optional<OidcProvider> findByTenantIdAndName(String tenantId, String name);

    Optional<OidcProvider> findByTenantIdAndNameAndActiveTrue(String tenantId, String name);

    Optional<OidcProvider> findByTenantIdAndIssuerAndActiveTrue(String tenantId, String issuer);

    boolean existsByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndIssuer(String tenantId, String issuer);

    long countByTenantIdAndActiveTrue(String tenantId);

    // ---- Legacy methods (used by SecurityConfig for JWT validation across all tenants) ----

    List<OidcProvider> findByActiveTrue();

    List<OidcProvider> findByActiveTrueOrderByNameAsc();

    Optional<OidcProvider> findByIdAndActiveTrue(String id);

    Optional<OidcProvider> findByName(String name);

    Optional<OidcProvider> findByNameAndActiveTrue(String name);

    Optional<OidcProvider> findByIssuer(String issuer);

    Optional<OidcProvider> findByIssuerAndActiveTrue(String issuer);

    boolean existsByName(String name);

    boolean existsByIssuer(String issuer);

    long countByActiveTrue();
}
