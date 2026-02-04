package com.emf.runtime.registry;

import com.emf.runtime.model.CollectionDefinition;

/**
 * Listener interface for collection definition changes in the registry.
 * 
 * <p>Implementations of this interface can be registered with a {@link CollectionRegistry}
 * to receive notifications when collections are registered, updated, or unregistered.
 * 
 * <p>Listeners are notified after the registry state has been updated, outside of any locks.
 * This means that by the time a listener is notified, the registry already reflects the change.
 * 
 * <p>Implementations should be thread-safe as notifications may come from multiple threads
 * concurrently.
 * 
 * @since 1.0.0
 * @see CollectionRegistry
 */
public interface CollectionChangeListener {
    
    /**
     * Called when a new collection is registered in the registry.
     * 
     * <p>This method is invoked when a collection is registered for the first time
     * (i.e., no previous definition existed with the same name).
     * 
     * @param definition the newly registered collection definition
     */
    void onCollectionRegistered(CollectionDefinition definition);
    
    /**
     * Called when an existing collection is updated in the registry.
     * 
     * <p>This method is invoked when a collection definition is replaced with a new version.
     * Both the old and new definitions are provided for comparison.
     * 
     * @param oldDefinition the previous collection definition
     * @param newDefinition the new collection definition that replaced it
     */
    void onCollectionUpdated(CollectionDefinition oldDefinition, CollectionDefinition newDefinition);
    
    /**
     * Called when a collection is unregistered from the registry.
     * 
     * <p>This method is invoked when a collection is removed from the registry.
     * Only the collection name is provided since the definition no longer exists.
     * 
     * @param collectionName the name of the unregistered collection
     */
    void onCollectionUnregistered(String collectionName);
}
