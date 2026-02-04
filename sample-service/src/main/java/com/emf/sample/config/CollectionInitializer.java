package com.emf.sample.config;

import com.emf.runtime.model.ApiConfig;
import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.CollectionDefinitionBuilder;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.StorageConfig;
import com.emf.runtime.model.StorageMode;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes collections for the sample service.
 * 
 * <p>This component defines and registers two collections:
 * <ul>
 *   <li><b>projects</b> - Project management collection with name, description, and status</li>
 *   <li><b>tasks</b> - Task management collection with title, description, completed flag, and project reference</li>
 * </ul>
 * 
 * <p>Collections are registered in the local registry and storage is initialized
 * using the StorageAdapter, which automatically creates database tables.
 */
@Component
public class CollectionInitializer {
    
    private static final Logger log = LoggerFactory.getLogger(CollectionInitializer.class);
    
    @Autowired
    private CollectionRegistry registry;
    
    @Autowired
    private StorageAdapter storageAdapter;
    
    /**
     * Initializes collections when the application is ready.
     * 
     * <p>This method is called after the Spring context is fully initialized
     * and all beans are ready. It runs with Order(1) to ensure it executes
     * before ControlPlaneRegistration.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void initializeCollections() {
        log.info("Initializing collections for sample service");
        
        // Define projects collection
        CollectionDefinition projects = new CollectionDefinitionBuilder()
            .name("projects")
            .displayName("Projects")
            .description("Project management collection")
            .addField(FieldDefinition.requiredString("name"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.enumField("status", 
                List.of("PLANNING", "ACTIVE", "COMPLETED", "ARCHIVED")))
            .storageConfig(new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_projects", null))
            .apiConfig(ApiConfig.allEnabled("/api/collections/projects"))
            .build();
        
        // Define tasks collection
        CollectionDefinition tasks = new CollectionDefinitionBuilder()
            .name("tasks")
            .displayName("Tasks")
            .description("Task management collection")
            .addField(FieldDefinition.requiredString("title"))
            .addField(FieldDefinition.string("description"))
            .addField(FieldDefinition.bool("completed", false))
            .addField(FieldDefinition.reference("project_id", "projects"))
            .storageConfig(new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_tasks", null))
            .apiConfig(ApiConfig.allEnabled("/api/collections/tasks"))
            .build();
        
        // Register collections in local registry
        log.info("Registering projects collection");
        registry.register(projects);
        
        log.info("Registering tasks collection");
        registry.register(tasks);
        
        // Initialize storage (creates tables)
        log.info("Initializing storage for projects collection");
        storageAdapter.initializeCollection(projects);
        
        log.info("Initializing storage for tasks collection");
        storageAdapter.initializeCollection(tasks);
        
        log.info("Collection initialization complete");
    }
}
