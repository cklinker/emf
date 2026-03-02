package com.emf.runtime.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for before-save hooks indexed by collection name.
 *
 * <p>Supports multiple hooks per collection, ordered by {@link BeforeSaveHook#getOrder()}.
 * Hooks are registered programmatically by the module system.
 *
 * <p>Wildcard support: hooks that return {@code "*"} from {@link BeforeSaveHook#getCollectionName()}
 * are applied to all collections. When looking up hooks for a collection, collection-specific
 * hooks are returned first (sorted by order), followed by wildcard hooks (sorted by order).
 *
 * <p>Thread-safe: uses ConcurrentHashMap for the handler registry.
 *
 * @since 1.0.0
 */
public class BeforeSaveHookRegistry {

    private static final Logger log = LoggerFactory.getLogger(BeforeSaveHookRegistry.class);

    /**
     * Wildcard collection name that matches all collections.
     */
    public static final String WILDCARD = "*";

    private final Map<String, List<BeforeSaveHook>> hooks = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     */
    public BeforeSaveHookRegistry() {
    }

    /**
     * Creates the registry and registers an initial list of hooks.
     *
     * @param discoveredHooks the hooks to register
     */
    public BeforeSaveHookRegistry(List<BeforeSaveHook> discoveredHooks) {
        if (discoveredHooks != null) {
            for (BeforeSaveHook hook : discoveredHooks) {
                register(hook);
            }
        }
        log.info("BeforeSaveHookRegistry initialized with hooks for {} collections: {}",
            hooks.size(), hooks.keySet());
    }

    /**
     * Registers a hook. Multiple hooks can be registered for the same collection.
     *
     * @param hook the hook to register
     */
    public void register(BeforeSaveHook hook) {
        String collectionName = hook.getCollectionName();
        hooks.computeIfAbsent(collectionName, k -> Collections.synchronizedList(new ArrayList<>()))
             .add(hook);
        // Re-sort by order after adding
        hooks.get(collectionName).sort(Comparator.comparingInt(BeforeSaveHook::getOrder));
        log.info("Registered BeforeSaveHook for collection '{}': {} (order={})",
            collectionName, hook.getClass().getSimpleName(), hook.getOrder());
    }

    /**
     * Gets the ordered list of hooks for the given collection.
     *
     * <p>Returns collection-specific hooks first, followed by wildcard hooks.
     * Both groups are individually sorted by {@link BeforeSaveHook#getOrder()}.
     *
     * @param collectionName the collection name
     * @return the hooks (collection-specific first, then wildcard), or empty list if none registered
     */
    public List<BeforeSaveHook> getHooks(String collectionName) {
        List<BeforeSaveHook> specific = hooks.getOrDefault(collectionName, List.of());
        List<BeforeSaveHook> wildcard = WILDCARD.equals(collectionName)
                ? List.of()
                : hooks.getOrDefault(WILDCARD, List.of());

        if (wildcard.isEmpty()) {
            return specific;
        }
        if (specific.isEmpty()) {
            return wildcard;
        }

        // Merge: collection-specific hooks first, then wildcard hooks
        List<BeforeSaveHook> merged = new ArrayList<>(specific.size() + wildcard.size());
        merged.addAll(specific);
        merged.addAll(wildcard);
        return Collections.unmodifiableList(merged);
    }

    /**
     * Checks if any hooks are registered for the given collection,
     * including wildcard hooks.
     *
     * @param collectionName the collection name
     * @return true if hooks are registered (collection-specific or wildcard)
     */
    public boolean hasHooks(String collectionName) {
        List<BeforeSaveHook> list = hooks.get(collectionName);
        if (list != null && !list.isEmpty()) {
            return true;
        }
        // Check for wildcard hooks (unless we're already looking up wildcards)
        if (!WILDCARD.equals(collectionName)) {
            List<BeforeSaveHook> wildcardList = hooks.get(WILDCARD);
            return wildcardList != null && !wildcardList.isEmpty();
        }
        return false;
    }

    /**
     * Returns the names of all collections that have hooks.
     *
     * @return set of collection names
     */
    public Set<String> getRegisteredCollections() {
        return Collections.unmodifiableSet(hooks.keySet());
    }

    /**
     * Returns the total number of registered hooks across all collections.
     *
     * @return hook count
     */
    public int getHookCount() {
        return hooks.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Evaluates all before-create hooks for a collection.
     * Returns the first error result if any hook fails, or merges field updates.
     *
     * @param collectionName the collection name
     * @param record the record data
     * @param tenantId the tenant ID
     * @return the combined result
     */
    public BeforeSaveResult evaluateBeforeCreate(String collectionName,
                                                  Map<String, Object> record, String tenantId) {
        List<BeforeSaveHook> collectionHooks = getHooks(collectionName);
        if (collectionHooks.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        Map<String, Object> mergedUpdates = new HashMap<>();
        for (BeforeSaveHook hook : collectionHooks) {
            BeforeSaveResult result = hook.beforeCreate(record, tenantId);
            if (!result.isSuccess()) {
                return result;
            }
            if (result.hasFieldUpdates()) {
                mergedUpdates.putAll(result.getFieldUpdates());
                record.putAll(result.getFieldUpdates());
            }
        }

        return mergedUpdates.isEmpty() ? BeforeSaveResult.ok()
                                       : BeforeSaveResult.withFieldUpdates(mergedUpdates);
    }

    /**
     * Evaluates all before-update hooks for a collection.
     * Returns the first error result if any hook fails, or merges field updates.
     *
     * @param collectionName the collection name
     * @param id the record ID
     * @param record the update data
     * @param previous the previous record data
     * @param tenantId the tenant ID
     * @return the combined result
     */
    public BeforeSaveResult evaluateBeforeUpdate(String collectionName, String id,
                                                  Map<String, Object> record,
                                                  Map<String, Object> previous, String tenantId) {
        List<BeforeSaveHook> collectionHooks = getHooks(collectionName);
        if (collectionHooks.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        Map<String, Object> mergedUpdates = new HashMap<>();
        for (BeforeSaveHook hook : collectionHooks) {
            BeforeSaveResult result = hook.beforeUpdate(id, record, previous, tenantId);
            if (!result.isSuccess()) {
                return result;
            }
            if (result.hasFieldUpdates()) {
                mergedUpdates.putAll(result.getFieldUpdates());
                record.putAll(result.getFieldUpdates());
            }
        }

        return mergedUpdates.isEmpty() ? BeforeSaveResult.ok()
                                       : BeforeSaveResult.withFieldUpdates(mergedUpdates);
    }

    /**
     * Invokes all after-create hooks for a collection, including wildcard hooks.
     *
     * @param collectionName the collection name
     * @param record the created record data
     * @param tenantId the tenant ID
     */
    public void invokeAfterCreate(String collectionName, Map<String, Object> record, String tenantId) {
        for (BeforeSaveHook hook : getHooks(collectionName)) {
            try {
                hook.afterCreate(collectionName, record, tenantId);
            } catch (Exception e) {
                log.error("After-create hook failed for collection '{}': {}",
                    collectionName, e.getMessage(), e);
            }
        }
    }

    /**
     * Invokes all after-update hooks for a collection, including wildcard hooks.
     *
     * @param collectionName the collection name
     * @param id the record ID
     * @param record the updated record data
     * @param previous the previous record data
     * @param tenantId the tenant ID
     */
    public void invokeAfterUpdate(String collectionName, String id,
                                   Map<String, Object> record, Map<String, Object> previous,
                                   String tenantId) {
        for (BeforeSaveHook hook : getHooks(collectionName)) {
            try {
                hook.afterUpdate(collectionName, id, record, previous, tenantId);
            } catch (Exception e) {
                log.error("After-update hook failed for collection '{}': {}",
                    collectionName, e.getMessage(), e);
            }
        }
    }

    /**
     * Invokes all after-delete hooks for a collection, including wildcard hooks.
     *
     * @param collectionName the collection name
     * @param id the deleted record ID
     * @param tenantId the tenant ID
     */
    public void invokeAfterDelete(String collectionName, String id, String tenantId) {
        for (BeforeSaveHook hook : getHooks(collectionName)) {
            try {
                hook.afterDelete(collectionName, id, tenantId);
            } catch (Exception e) {
                log.error("After-delete hook failed for collection '{}': {}",
                    collectionName, e.getMessage(), e);
            }
        }
    }
}
