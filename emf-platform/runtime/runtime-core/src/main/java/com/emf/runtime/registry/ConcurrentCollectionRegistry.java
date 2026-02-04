package com.emf.runtime.registry;

import com.emf.runtime.model.CollectionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe implementation of {@link CollectionRegistry} using copy-on-write semantics.
 * 
 * <p>This implementation provides:
 * <ul>
 *   <li><b>Lock-free reads:</b> Read operations ({@link #get(String)}, {@link #getAllCollectionNames()})
 *       do not acquire any locks and can proceed concurrently without blocking.</li>
 *   <li><b>Serialized writes:</b> Write operations ({@link #register(CollectionDefinition)}, 
 *       {@link #unregister(String)}) acquire a write lock to ensure atomic updates.</li>
 *   <li><b>Copy-on-write:</b> Each write creates a new immutable map, ensuring readers always
 *       see a consistent snapshot.</li>
 *   <li><b>Version tracking:</b> Collection versions are tracked and incremented on updates.</li>
 *   <li><b>Listener notifications:</b> Listeners are notified after the lock is released to
 *       prevent deadlocks and allow listeners to call back into the registry.</li>
 * </ul>
 * 
 * <p>The implementation uses a volatile reference to an immutable map for the collection store,
 * which provides visibility guarantees for concurrent reads. A {@link ReentrantReadWriteLock}
 * is used only for write operations to serialize updates.
 * 
 * <p>Listeners are stored in a {@link CopyOnWriteArrayList} which is thread-safe for iteration
 * and modification from multiple threads.
 * 
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Multiple threads can safely call any method concurrently.
 * 
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>Read operations: O(1) average case, no locking</li>
 *   <li>Write operations: O(n) where n is the number of collections (due to map copy)</li>
 *   <li>Listener notification: O(m) where m is the number of listeners</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see CollectionRegistry
 * @see CollectionChangeListener
 */
public class ConcurrentCollectionRegistry implements CollectionRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentCollectionRegistry.class);
    
    /**
     * Volatile reference to immutable map for copy-on-write semantics.
     * The volatile keyword ensures visibility of the reference across threads.
     * The immutable map ensures readers see a consistent snapshot.
     */
    private volatile Map<String, CollectionDefinition> collections = Map.of();
    
    /**
     * Lock for write operations only.
     * Read operations do not acquire this lock.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * Thread-safe list of listeners.
     * CopyOnWriteArrayList allows safe iteration during notification while
     * other threads may add/remove listeners.
     */
    private final CopyOnWriteArrayList<CollectionChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * Internal version counter for tracking registry-wide changes.
     * Incremented on every write operation.
     */
    private volatile long registryVersion = 0;
    
    /**
     * Creates a new empty concurrent collection registry.
     */
    public ConcurrentCollectionRegistry() {
        logger.debug("ConcurrentCollectionRegistry initialized");
    }
    
    @Override
    public void register(CollectionDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        
        CollectionDefinition oldDefinition = null;
        CollectionDefinition registeredDefinition;
        
        lock.writeLock().lock();
        try {
            // Create a mutable copy of the current map
            Map<String, CollectionDefinition> newMap = new HashMap<>(collections);
            
            // Check if this is an update or new registration
            oldDefinition = newMap.get(definition.name());
            
            if (oldDefinition != null) {
                // Update: increment version if not already incremented
                if (definition.version() <= oldDefinition.version()) {
                    registeredDefinition = definition.withIncrementedVersion();
                } else {
                    registeredDefinition = definition;
                }
                logger.debug("Updating collection '{}' from version {} to version {}", 
                    definition.name(), oldDefinition.version(), registeredDefinition.version());
            } else {
                // New registration
                registeredDefinition = definition;
                logger.debug("Registering new collection '{}' with version {}", 
                    definition.name(), registeredDefinition.version());
            }
            
            newMap.put(definition.name(), registeredDefinition);
            
            // Replace with immutable snapshot
            collections = Map.copyOf(newMap);
            registryVersion++;
            
        } finally {
            lock.writeLock().unlock();
        }
        
        // Notify listeners outside the lock to prevent deadlocks
        // and allow listeners to call back into the registry
        if (oldDefinition == null) {
            notifyRegistered(registeredDefinition);
        } else {
            notifyUpdated(oldDefinition, registeredDefinition);
        }
    }
    
    @Override
    public CollectionDefinition get(String collectionName) {
        // No lock needed - reading volatile reference to immutable map
        // Handle null gracefully - immutable maps throw NPE on null keys
        if (collectionName == null) {
            return null;
        }
        return collections.get(collectionName);
    }
    
    @Override
    public Set<String> getAllCollectionNames() {
        // No lock needed - the keySet of an immutable map is also immutable
        return collections.keySet();
    }
    
    @Override
    public void unregister(String collectionName) {
        if (collectionName == null) {
            return;
        }
        
        boolean wasRemoved = false;
        
        lock.writeLock().lock();
        try {
            if (collections.containsKey(collectionName)) {
                // Create a mutable copy and remove the collection
                Map<String, CollectionDefinition> newMap = new HashMap<>(collections);
                newMap.remove(collectionName);
                
                // Replace with immutable snapshot
                collections = Map.copyOf(newMap);
                registryVersion++;
                wasRemoved = true;
                
                logger.debug("Unregistered collection '{}'", collectionName);
            }
        } finally {
            lock.writeLock().unlock();
        }
        
        // Notify listeners outside the lock
        if (wasRemoved) {
            notifyUnregistered(collectionName);
        }
    }
    
    @Override
    public void addListener(CollectionChangeListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.add(listener);
        logger.debug("Added collection change listener: {}", listener.getClass().getSimpleName());
    }
    
    @Override
    public void removeListener(CollectionChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            logger.debug("Removed collection change listener: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * Gets the current number of registered collections.
     * 
     * @return the number of collections in the registry
     */
    public int size() {
        return collections.size();
    }
    
    /**
     * Checks if the registry is empty.
     * 
     * @return true if no collections are registered, false otherwise
     */
    public boolean isEmpty() {
        return collections.isEmpty();
    }
    
    /**
     * Checks if a collection with the given name is registered.
     * 
     * @param collectionName the collection name to check
     * @return true if the collection is registered, false otherwise
     */
    public boolean contains(String collectionName) {
        return collections.containsKey(collectionName);
    }
    
    /**
     * Gets the current registry version.
     * 
     * <p>The registry version is incremented on every write operation
     * (register or unregister). This can be used to detect changes
     * to the registry.
     * 
     * @return the current registry version
     */
    public long getRegistryVersion() {
        return registryVersion;
    }
    
    /**
     * Gets the number of registered listeners.
     * 
     * @return the number of listeners
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * Notifies all listeners that a new collection was registered.
     * 
     * @param definition the newly registered collection
     */
    private void notifyRegistered(CollectionDefinition definition) {
        for (CollectionChangeListener listener : listeners) {
            try {
                listener.onCollectionRegistered(definition);
            } catch (Exception e) {
                logger.error("Error notifying listener of collection registration for '{}': {}", 
                    definition.name(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Notifies all listeners that a collection was updated.
     * 
     * @param oldDefinition the previous collection definition
     * @param newDefinition the new collection definition
     */
    private void notifyUpdated(CollectionDefinition oldDefinition, CollectionDefinition newDefinition) {
        for (CollectionChangeListener listener : listeners) {
            try {
                listener.onCollectionUpdated(oldDefinition, newDefinition);
            } catch (Exception e) {
                logger.error("Error notifying listener of collection update for '{}': {}", 
                    newDefinition.name(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Notifies all listeners that a collection was unregistered.
     * 
     * @param collectionName the name of the unregistered collection
     */
    private void notifyUnregistered(String collectionName) {
        for (CollectionChangeListener listener : listeners) {
            try {
                listener.onCollectionUnregistered(collectionName);
            } catch (Exception e) {
                logger.error("Error notifying listener of collection unregistration for '{}': {}", 
                    collectionName, e.getMessage(), e);
            }
        }
    }
}
