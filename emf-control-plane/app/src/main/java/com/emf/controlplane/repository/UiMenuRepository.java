package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UiMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UiMenu entity operations.
 */
@Repository
public interface UiMenuRepository extends JpaRepository<UiMenu, String> {

    /**
     * Find menu by name.
     */
    Optional<UiMenu> findByName(String name);

    /**
     * Check if a menu with the given name exists.
     */
    boolean existsByName(String name);

    /**
     * Find all menus ordered by name.
     */
    List<UiMenu> findAllByOrderByNameAsc();

    /**
     * Find all menus with their items eagerly fetched, ordered by name.
     */
    @Query("SELECT DISTINCT m FROM UiMenu m LEFT JOIN FETCH m.items ORDER BY m.name ASC")
    List<UiMenu> findAllWithItemsOrderByNameAsc();
}
