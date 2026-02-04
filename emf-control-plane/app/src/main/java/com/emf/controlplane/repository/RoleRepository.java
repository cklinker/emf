package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Role entity operations.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, String> {

    /**
     * Find role by name.
     */
    Optional<Role> findByName(String name);

    /**
     * Check if a role with the given name exists.
     */
    boolean existsByName(String name);

    /**
     * Find all roles ordered by name.
     */
    List<Role> findAllByOrderByNameAsc();
}
