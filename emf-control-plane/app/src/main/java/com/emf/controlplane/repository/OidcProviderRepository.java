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

    /**
     * Find all active OIDC providers.
     */
    List<OidcProvider> findByActiveTrue();

    /**
     * Find all active OIDC providers ordered by name.
     */
    List<OidcProvider> findByActiveTrueOrderByNameAsc();

    /**
     * Find active provider by ID.
     */
    Optional<OidcProvider> findByIdAndActiveTrue(String id);

    /**
     * Find provider by name.
     */
    Optional<OidcProvider> findByName(String name);

    /**
     * Find active provider by name.
     */
    Optional<OidcProvider> findByNameAndActiveTrue(String name);

    /**
     * Find provider by issuer.
     */
    Optional<OidcProvider> findByIssuer(String issuer);

    /**
     * Find active provider by issuer.
     */
    Optional<OidcProvider> findByIssuerAndActiveTrue(String issuer);

    /**
     * Check if a provider with the given name exists.
     */
    boolean existsByName(String name);

    /**
     * Check if a provider with the given issuer exists.
     */
    boolean existsByIssuer(String issuer);

    /**
     * Count active providers.
     */
    long countByActiveTrue();
}
