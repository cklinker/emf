package com.emf.sample.listener;

import com.emf.runtime.events.CollectionEvent;
import com.emf.sample.service.ResourceCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener that caches resources on create/update and invalidates on delete.
 * 
 * <p>This listener responds to {@link CollectionEvent} published by the runtime-core
 * library and updates the Redis cache accordingly:
 * <ul>
 *   <li>CREATE events - cache the new resource</li>
 *   <li>UPDATE events - update the cached resource</li>
 *   <li>DELETE events - invalidate the cached resource</li>
 * </ul>
 */
@Component
public class CacheEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(CacheEventListener.class);
    
    @Autowired
    private ResourceCacheService cacheService;
    
    /**
     * Handles collection events and updates the cache accordingly.
     * 
     * @param event the collection event
     */
    @EventListener
    public void onCollectionEvent(CollectionEvent event) {
        String type = event.collectionName();
        String id = event.recordId();
        
        log.debug("Received collection event: type={}, id={}, eventType={}", 
            type, id, event.eventType());
        
        switch (event.eventType()) {
            case CREATE, UPDATE -> {
                if (event.data() != null) {
                    cacheService.cacheResource(type, id, event.data());
                }
            }
            case DELETE -> {
                cacheService.invalidateResource(type, id);
            }
        }
    }
}
