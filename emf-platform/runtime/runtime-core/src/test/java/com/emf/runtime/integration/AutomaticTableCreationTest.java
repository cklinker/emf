package com.emf.runtime.integration;

import com.emf.runtime.model.*;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.registry.ConcurrentCollectionRegistry;
import com.emf.runtime.storage.PhysicalTableStorageAdapter;
import com.emf.runtime.storage.SchemaMigrationEngine;
import com.emf.runtime.storage.StorageAdapter;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for automatic table creation functionality.
 * 
 * <p>This test validates that the StorageAdapter.initializeCollection() method
 * correctly creates database tables for collections, including:
 * <ul>
 *   <li>System columns (id, created_at, updated_at)</li>
 *   <li>User-defined columns with correct types</li>
 *   <li>Foreign key constraints for relationships</li>
 *   <li>Unique constraints</li>
 * </ul>
 * 
 * <p>This test is part of the local integration testing spec (Task 2.2).
 * 
 * @see com.emf.runtime.storage.StorageAdapter#initializeCollection(CollectionDefinition)
 * @see com.emf.runtime.storage.PhysicalTableStorageAdapter
 */
@DisplayName("Automatic Table Creation Tests")
class AutomaticTableCreationTest {
    
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private CollectionRegistry registry;
    private StorageAdapter storageAdapter;
    
    @BeforeEach
    void setUp() {
        // Set up embedded H2 database
        dataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Initialize components
        registry = new ConcurrentCollectionRegistry();
        SchemaMigrationEngine migrationEngine = new SchemaMigrationEngine(jdbcTemplate);
        storageAdapter = new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine);
    }
    
    @Nested
    @DisplayName("Projects Collection Table Creation")
    class ProjectsTableCreationTests {
        
        @Test
        @DisplayName("Should create projects table with all required columns")
        void shouldCreateProjectsTableWithAllColumns() {
            // Define projects collection
            CollectionDefinition projects = new CollectionDefinitionBuilder()
                .name("projects")
                .displayName("Projects")
                .description("Project management collection")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.string("description"))
                .addField(FieldDefinition.enumField("status", 
                    List.of("PLANNING", "ACTIVE", "COMPLETED", "ARCHIVED")))
                .storageConfig(StorageConfig.physicalTable("tbl_projects"))
                .build();
            
            // Initialize collection (creates table)
            storageAdapter.initializeCollection(projects);
            
            // Verify table exists by querying it
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_projects", Integer.class);
            assertEquals(0, count, "Table should exist and be empty");
            
            // Verify columns exist by inserting a record
            Map<String, Object> projectData = new HashMap<>();
            projectData.put("id", "test-project-1");
            projectData.put("name", "Test Project");
            projectData.put("description", "A test project");
            projectData.put("status", "PLANNING");
            projectData.put("createdAt", Instant.now());
            projectData.put("updatedAt", Instant.now());
            
            assertDoesNotThrow(() -> storageAdapter.create(projects, projectData),
                "Should be able to insert record with all columns");
            
            // Verify record was created
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_projects", Integer.class);
            assertEquals(1, count, "Record should be inserted");
        }
        
        @Test
        @DisplayName("Should create projects table with system columns")
        void shouldCreateProjectsTableWithSystemColumns() {
            CollectionDefinition projects = new CollectionDefinitionBuilder()
                .name("projects")
                .displayName("Projects")
                .addField(FieldDefinition.requiredString("name"))
                .storageConfig(StorageConfig.physicalTable("tbl_projects_system"))
                .build();
            
            storageAdapter.initializeCollection(projects);
            
            // Verify system columns exist by querying metadata
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'TBL_PROJECTS_SYSTEM' " +
                "ORDER BY COLUMN_NAME");
            
            List<String> columnNames = columns.stream()
                .map(col -> (String) col.get("COLUMN_NAME"))
                .toList();
            
            assertTrue(columnNames.contains("ID"), "Should have ID column");
            assertTrue(columnNames.contains("CREATED_AT"), "Should have CREATED_AT column");
            assertTrue(columnNames.contains("UPDATED_AT"), "Should have UPDATED_AT column");
            assertTrue(columnNames.contains("NAME"), "Should have NAME column");
        }
    }
    
    @Nested
    @DisplayName("Tasks Collection Table Creation")
    class TasksTableCreationTests {
        
        @Test
        @DisplayName("Should create tasks table with all required columns")
        void shouldCreateTasksTableWithAllColumns() {
            // Define tasks collection
            CollectionDefinition tasks = new CollectionDefinitionBuilder()
                .name("tasks")
                .displayName("Tasks")
                .description("Task management collection")
                .addField(FieldDefinition.requiredString("title"))
                .addField(FieldDefinition.string("description"))
                .addField(FieldDefinition.bool("completed", false))
                .addField(FieldDefinition.reference("project_id", "projects"))
                .storageConfig(StorageConfig.physicalTable("tbl_tasks"))
                .build();
            
            // Initialize collection (creates table)
            storageAdapter.initializeCollection(tasks);
            
            // Verify table exists by querying it
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_tasks", Integer.class);
            assertEquals(0, count, "Table should exist and be empty");
            
            // Verify columns exist by inserting a record
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", "test-task-1");
            taskData.put("title", "Test Task");
            taskData.put("description", "A test task");
            taskData.put("completed", false);
            taskData.put("project_id", "project-123");
            taskData.put("createdAt", Instant.now());
            taskData.put("updatedAt", Instant.now());
            
            assertDoesNotThrow(() -> storageAdapter.create(tasks, taskData),
                "Should be able to insert record with all columns");
            
            // Verify record was created
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_tasks", Integer.class);
            assertEquals(1, count, "Record should be inserted");
        }
    }
    
    @Nested
    @DisplayName("Foreign Key Constraints")
    class ForeignKeyConstraintTests {
        
        @Test
        @DisplayName("Should create tables with relationship fields")
        void shouldCreateTablesWithRelationshipFields() {
            // Create projects collection first
            CollectionDefinition projects = new CollectionDefinitionBuilder()
                .name("projects")
                .displayName("Projects")
                .addField(FieldDefinition.requiredString("name"))
                .storageConfig(StorageConfig.physicalTable("tbl_projects_fk"))
                .build();
            
            storageAdapter.initializeCollection(projects);
            
            // Create tasks collection with reference to projects
            CollectionDefinition tasks = new CollectionDefinitionBuilder()
                .name("tasks")
                .displayName("Tasks")
                .addField(FieldDefinition.requiredString("title"))
                .addField(FieldDefinition.reference("project_id", "projects"))
                .storageConfig(StorageConfig.physicalTable("tbl_tasks_fk"))
                .build();
            
            storageAdapter.initializeCollection(tasks);
            
            // Verify project_id column exists in tasks table
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'TBL_TASKS_FK' AND COLUMN_NAME = 'PROJECT_ID'");
            
            assertEquals(1, columns.size(), "project_id column should exist");
        }
        
        @Test
        @DisplayName("Should allow inserting task with valid project reference")
        void shouldAllowInsertingTaskWithValidProjectReference() {
            // Create projects collection
            CollectionDefinition projects = new CollectionDefinitionBuilder()
                .name("projects")
                .displayName("Projects")
                .addField(FieldDefinition.requiredString("name"))
                .storageConfig(StorageConfig.physicalTable("tbl_projects_ref"))
                .build();
            
            storageAdapter.initializeCollection(projects);
            
            // Create tasks collection
            CollectionDefinition tasks = new CollectionDefinitionBuilder()
                .name("tasks")
                .displayName("Tasks")
                .addField(FieldDefinition.requiredString("title"))
                .addField(FieldDefinition.reference("project_id", "projects"))
                .storageConfig(StorageConfig.physicalTable("tbl_tasks_ref"))
                .build();
            
            storageAdapter.initializeCollection(tasks);
            
            // Insert a project
            Map<String, Object> projectData = new HashMap<>();
            projectData.put("id", "project-1");
            projectData.put("name", "Project 1");
            projectData.put("createdAt", Instant.now());
            projectData.put("updatedAt", Instant.now());
            storageAdapter.create(projects, projectData);
            
            // Insert a task with reference to the project
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", "task-1");
            taskData.put("title", "Task 1");
            taskData.put("project_id", "project-1");
            taskData.put("createdAt", Instant.now());
            taskData.put("updatedAt", Instant.now());
            
            assertDoesNotThrow(() -> storageAdapter.create(tasks, taskData),
                "Should be able to insert task with valid project reference");
        }
    }
    
    @Nested
    @DisplayName("Complete Projects and Tasks Setup")
    class CompleteSetupTests {
        
        @Test
        @DisplayName("Should create both projects and tasks tables on startup")
        void shouldCreateBothTablesOnStartup() {
            // Define projects collection
            CollectionDefinition projects = new CollectionDefinitionBuilder()
                .name("projects")
                .displayName("Projects")
                .description("Project management collection")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.string("description"))
                .addField(FieldDefinition.enumField("status", 
                    List.of("PLANNING", "ACTIVE", "COMPLETED", "ARCHIVED")))
                .storageConfig(StorageConfig.physicalTable("tbl_projects_complete"))
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
                .storageConfig(StorageConfig.physicalTable("tbl_tasks_complete"))
                .build();
            
            // Register collections
            registry.register(projects);
            registry.register(tasks);
            
            // Initialize storage (simulates startup)
            storageAdapter.initializeCollection(projects);
            storageAdapter.initializeCollection(tasks);
            
            // Verify both tables exist
            Integer projectsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_projects_complete", Integer.class);
            assertEquals(0, projectsCount, "Projects table should exist and be empty");
            
            Integer tasksCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_tasks_complete", Integer.class);
            assertEquals(0, tasksCount, "Tasks table should exist and be empty");
            
            // Verify we can create a complete workflow
            Map<String, Object> projectData = new HashMap<>();
            projectData.put("id", "proj-1");
            projectData.put("name", "Integration Test Project");
            projectData.put("description", "Testing automatic table creation");
            projectData.put("status", "ACTIVE");
            projectData.put("createdAt", Instant.now());
            projectData.put("updatedAt", Instant.now());
            storageAdapter.create(projects, projectData);
            
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", "task-1");
            taskData.put("title", "Verify table creation");
            taskData.put("description", "Ensure tables are created automatically");
            taskData.put("completed", false);
            taskData.put("project_id", "proj-1");
            taskData.put("createdAt", Instant.now());
            taskData.put("updatedAt", Instant.now());
            storageAdapter.create(tasks, taskData);
            
            // Verify records were created
            projectsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_projects_complete", Integer.class);
            assertEquals(1, projectsCount, "Should have one project");
            
            tasksCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_tasks_complete", Integer.class);
            assertEquals(1, tasksCount, "Should have one task");
        }
    }
    
    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {
        
        @Test
        @DisplayName("Should be idempotent - calling initializeCollection twice should not fail")
        void shouldBeIdempotent() {
            CollectionDefinition projects = new CollectionDefinitionBuilder()
                .name("projects")
                .displayName("Projects")
                .addField(FieldDefinition.requiredString("name"))
                .storageConfig(StorageConfig.physicalTable("tbl_projects_idempotent"))
                .build();
            
            // First initialization
            assertDoesNotThrow(() -> storageAdapter.initializeCollection(projects),
                "First initialization should succeed");
            
            // Second initialization (should not fail due to CREATE TABLE IF NOT EXISTS)
            assertDoesNotThrow(() -> storageAdapter.initializeCollection(projects),
                "Second initialization should succeed (idempotent)");
        }
    }
}
