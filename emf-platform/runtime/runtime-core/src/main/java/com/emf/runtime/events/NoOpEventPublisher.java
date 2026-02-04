package com.emf.runtime.events;

import com.emf.runtime.model.CollectionDefinition;

import java.util.Map;

/**
 * No-operation implementation of EventPublisher.
 * 
 * <p>This implementation does nothing when events are published. It is useful
 * for testing or when event publishing is not required.
 * 
 * @since 1.0.0
 */
public class NoOpEventPublisher implements EventPublisher {
    
    @Override
    public void publishCreate(CollectionDefinition definition, Map<String, Object> data) {
        // No-op
    }
    
    @Override
    public void publishUpdate(CollectionDefinition definition, Map<String, Object> data) {
        // No-op
    }
    
    @Override
    public void publishDelete(CollectionDefinition definition, String recordId) {
        // No-op
    }
    
    @Override
    public void publish(CollectionDefinition definition, CollectionEvent event) {
        // No-op
    }
}
