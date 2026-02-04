package com.emf.sample.service;

import com.emf.runtime.events.CollectionEvent;
import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

/**
 * Query engine wrapper that publishes Spring application events for CRUD operations.
 * 
 * <p>This wrapper delegates all operations to the underlying query engine and publishes
 * {@link CollectionEvent} instances as Spring application events that can be consumed
 * by {@link org.springframework.context.event.EventListener} annotated methods.
 */
public class EventPublishingQueryEngine implements QueryEngine {
    
    private final QueryEngine delegate;
    private final ApplicationEventPublisher eventPublisher;
    
    public EventPublishingQueryEngine(QueryEngine delegate, ApplicationEventPublisher eventPublisher) {
        this.delegate = delegate;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public QueryResult executeQuery(CollectionDefinition definition, QueryRequest request) {
        return delegate.executeQuery(definition, request);
    }
    
    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        return delegate.getById(definition, id);
    }
    
    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        Map<String, Object> created = delegate.create(definition, data);
        
        // Publish CREATE event
        String id = (String) created.get("id");
        CollectionEvent event = CollectionEvent.create(definition.name(), id, created);
        eventPublisher.publishEvent(event);
        
        return created;
    }
    
    @Override
    public Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data) {
        Optional<Map<String, Object>> updated = delegate.update(definition, id, data);
        
        // Publish UPDATE event if record was found and updated
        updated.ifPresent(record -> {
            CollectionEvent event = CollectionEvent.update(definition.name(), id, record);
            eventPublisher.publishEvent(event);
        });
        
        return updated;
    }
    
    @Override
    public boolean delete(CollectionDefinition definition, String id) {
        boolean deleted = delegate.delete(definition, id);
        
        // Publish DELETE event if record was found and deleted
        if (deleted) {
            CollectionEvent event = CollectionEvent.delete(definition.name(), id);
            eventPublisher.publishEvent(event);
        }
        
        return deleted;
    }
}
