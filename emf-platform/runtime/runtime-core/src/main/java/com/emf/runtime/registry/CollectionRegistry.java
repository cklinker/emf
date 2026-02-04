package com.emf.runtime.registry;

import com.emf.runtime.model.CollectionDefinition;

import java.util.Set;

/**
 * Thread-safe registry for collection definitions.
 * 
 * <p>The registry provides a central location for storing and retrieving collection definitions
 * at runtime. It supports:
 * <ul>
 *   <li>Atomic updates using copy-on-write semantics</li>
 *   <li>Concurrent read operations without blocking</li>
 *   <li>Change listener notifications</li>
 *   <li>Version tracking for each collection</li>
 * </ul>
 * 
 * <p>Read operations ({@link #get(String)}, {@link #getAllCollectionNames()}) are lock-free
 * and can be called concurrently from multiple threads without blocking.
 * 
 * <p>Write operations ({@link #register(CollectionDefinition)}, {@link #unregister(String)})
 * are serialized to prevent race conditions and ensure atomic updates.
 * 
 * <p>Listeners are notified after the registry state has been updated, outside of any locks.
 * 
 * @since 1.0.0
 * @see CollectionDefinition
 * @see CollectionChangeListener
 */
public interface CollectionRegistry {
    
    /**
     * Registers or updates a collection definition.
     * 
     * <p>If a collection with the same name already exists, it will be replaced
     * with the new definition. Registered listeners will be notified of the change:
     * <ul>
     *   <li>{@link CollectionChangeListener#onCollectionRegistered(CollectionDefinition)} 
     *       for new collections</li>
     *   <li>{@link CollectionChangeListener#onCollectionUpdated(CollectionDefinition, CollectionDefinition)} 
     *       for existing collections</li>
     * </ul>
     * 
     * <p>This operation uses copy-on-write semantics for thread safety.
     * 
     * @param definition the collection definition to register (must not be null)
     * @throws NullPointerException if definition is null
     */
    void register(CollectionDefinition definition);
    
    /**
     * Gets a collection definition by name.
     * 
     * <p>This is a lock-free read operation that can be called concurrently
     * from multiple threads without blocking.
     * 
     * @param collectionName the name of the collection to retrieve
     * @return the collection definition, or null if not found
     */
    CollectionDefinition get(String collectionName);
    
    /**
     * Gets all registered collection names.
     * 
     * <p>This is a lock-free read operation that returns an immutable snapshot
     * of the current collection names.
     * 
     * @return an immutable set of all registered collection names
     */
    Set<String> getAllCollectionNames();
    
    /**
     * Removes a collection definition from the registry.
     * 
     * <p>If the collection exists, registered listeners will be notified via
     * {@link CollectionChangeListener#onCollectionUnregistered(String)}.
     * 
     * <p>If the collection does not exist, this method does nothing.
     * 
     * @param collectionName the name of the collection to remove
     */
    void unregister(String collectionName);
    
    /**
     * Adds a listener for collection changes.
     * 
     * <p>The listener will be notified of all future collection changes
     * (register, update, unregister). Listeners are stored in a thread-safe
     * collection and can be added from any thread.
     * 
     * <p>Adding the same listener multiple times will result in multiple notifications.
     * 
     * @param listener the listener to add (must not be null)
     * @throws NullPointerException if listener is null
     */
    void addListener(CollectionChangeListener listener);
    
    /**
     * Removes a listener from the registry.
     * 
     * <p>The listener will no longer receive notifications of collection changes.
     * If the listener was not previously added, this method does nothing.
     * 
     * @param listener the listener to remove
     */
    void removeListener(CollectionChangeListener listener);
}
