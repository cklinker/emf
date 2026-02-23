package com.emf.runtime.workflow;

import java.util.Map;

/**
 * Interface for collection lifecycle hooks that execute during record
 * create/update/delete operations.
 *
 * <p>Before-save hooks can:
 * <ul>
 *   <li>Validate data and return errors (blocking the operation)</li>
 *   <li>Transform data via field updates (applied before persist)</li>
 *   <li>Set default values</li>
 * </ul>
 *
 * <p>After-save hooks can:
 * <ul>
 *   <li>Publish events</li>
 *   <li>Seed related data</li>
 *   <li>Invalidate caches</li>
 *   <li>Trigger side effects</li>
 * </ul>
 *
 * <p>Hooks are registered via the module system and indexed by collection name
 * in the {@link BeforeSaveHookRegistry}.
 *
 * @since 1.0.0
 */
public interface BeforeSaveHook {

    /**
     * Returns the collection name this hook manages.
     *
     * @return the collection name (e.g., "users", "collections", "fields")
     */
    String getCollectionName();

    /**
     * Returns the execution order for this hook. Lower values execute first.
     * Default is 0. Use negative values to run before other hooks, positive to run after.
     *
     * @return the execution order
     */
    default int getOrder() {
        return 0;
    }

    /**
     * Called before a new record is created. Can validate data, set defaults,
     * or return field updates to apply before persist.
     *
     * @param record the record data being created
     * @param tenantId the tenant ID
     * @return the result (OK to proceed, field updates, or validation errors)
     */
    default BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        return BeforeSaveResult.ok();
    }

    /**
     * Called before an existing record is updated. Can validate data,
     * enforce constraints, or return field updates.
     *
     * @param id the record ID
     * @param record the update data
     * @param previous the previous record data
     * @param tenantId the tenant ID
     * @return the result (OK to proceed, field updates, or validation errors)
     */
    default BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                           Map<String, Object> previous, String tenantId) {
        return BeforeSaveResult.ok();
    }

    /**
     * Called after a new record is created. Used for side effects like
     * event publishing, seeding defaults, or cache invalidation.
     *
     * @param record the created record data
     * @param tenantId the tenant ID
     */
    default void afterCreate(Map<String, Object> record, String tenantId) {
        // No-op by default
    }

    /**
     * Called after an existing record is updated.
     *
     * @param id the record ID
     * @param record the updated record data
     * @param previous the previous record data
     * @param tenantId the tenant ID
     */
    default void afterUpdate(String id, Map<String, Object> record,
                              Map<String, Object> previous, String tenantId) {
        // No-op by default
    }

    /**
     * Called after a record is deleted.
     *
     * @param id the deleted record ID
     * @param tenantId the tenant ID
     */
    default void afterDelete(String id, String tenantId) {
        // No-op by default
    }
}
