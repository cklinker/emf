package com.emf.runtime.module;

import java.util.List;
import java.util.Optional;

/**
 * Storage interface for tenant module data.
 * Implemented by {@code JdbcModuleStore} in the worker service.
 *
 * @since 1.0.0
 */
public interface ModuleStore {

    /**
     * Creates a new tenant module record.
     *
     * @param data the module data to persist
     * @return the persisted module ID
     */
    String createModule(TenantModuleData data);

    /**
     * Creates action records for a module.
     *
     * @param actions the actions to persist
     */
    void createActions(List<TenantModuleData.TenantModuleActionData> actions);

    /**
     * Loads a module by tenant and module identifier.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier from the manifest
     * @return the module data, or empty if not found
     */
    Optional<TenantModuleData> findByTenantAndModuleId(String tenantId, String moduleId);

    /**
     * Loads a module by its primary key.
     *
     * @param id the primary key
     * @return the module data, or empty if not found
     */
    Optional<TenantModuleData> findById(String id);

    /**
     * Lists all modules for a tenant.
     *
     * @param tenantId the tenant ID
     * @return all modules for the tenant
     */
    List<TenantModuleData> findByTenant(String tenantId);

    /**
     * Lists all modules for a tenant with a specific status.
     *
     * @param tenantId the tenant ID
     * @param status the status filter
     * @return matching modules
     */
    List<TenantModuleData> findByTenantAndStatus(String tenantId, String status);

    /**
     * Updates the status of a module.
     *
     * @param id the module primary key
     * @param status the new status
     */
    void updateStatus(String id, String status);

    /**
     * Deletes a module and its associated actions.
     *
     * @param id the module primary key
     */
    void deleteModule(String id);

    /**
     * Lists all active modules across all tenants (for pod startup loading).
     *
     * @return all active modules
     */
    List<TenantModuleData> findAllActive();
}
