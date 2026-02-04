# Implementation Plan: Platform Core Runtime

## Overview

This implementation plan breaks down the Platform Core Runtime into discrete, incremental coding tasks. Each task builds on previous work, with property-based tests integrated throughout to validate correctness early. The plan follows a bottom-up approach: core models → registry → storage → validation → query engine → routing → events.

## Tasks

- [x] 1. Set up project structure and core model definitions
  - Create Maven multi-module structure under `emf-platform/runtime/`
  - Define module: `runtime-core` with Spring Boot dependencies
  - Create package structure: `model/`, `registry/`, `router/`, `query/`, `validation/`, `storage/`, `events/`, `config/`
  - Add dependencies: Spring Boot 3.x, PostgreSQL JDBC, Spring Kafka, Spring Data Redis, jqwik for property testing
  - Configure Maven for Java 21+ with records and sealed interfaces
  - _Requirements: 1.1, 1.2_

- [x] 2. Implement collection and field definition models
  - [x] 2.1 Create core model records (CollectionDefinition, FieldDefinition, FieldType enum, ValidationRules, configuration records)
    - Implement Java records with defensive copying for immutable collections
    - Add validation in record constructors for required fields
    - Implement helper methods (e.g., `getField(String fieldName)`)
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ]* 2.2 Write property test for collection definition serialization round-trip
    - **Property 1: Collection definition serialization round-trip**
    - **Validates: Requirements 1.4**

  - [x] 2.3 Implement builder classes (CollectionDefinitionBuilder, FieldDefinitionBuilder)
    - Implement builder pattern with method chaining
    - Add validation in `build()` methods for required fields
    - Set sensible defaults (Mode A storage, enabled API operations)
    - _Requirements: 13.1, 13.2, 13.3, 13.5_

  - [ ]* 2.4 Write property test for builder validation
    - **Property 2: Collection definition builder validation**
    - **Validates: Requirements 1.3, 13.3**

  - [ ]* 2.5 Write property test for collection definition immutability
    - **Property 3: Collection definition immutability**
    - **Validates: Requirements 13.5**

- [x] 3. Implement Runtime Registry
  - [x] 3.1 Create CollectionRegistry interface and ConcurrentCollectionRegistry implementation
    - Implement copy-on-write semantics using volatile reference to immutable map
    - Use ReentrantReadWriteLock for write operations only
    - Implement version tracking (increment on updates)
    - Add listener support with CopyOnWriteArrayList
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 15.1, 15.2_

  - [ ]* 3.2 Write property test for registry concurrent read consistency
    - **Property 4: Registry concurrent read consistency**
    - **Validates: Requirements 2.1, 2.2, 2.5, 15.1**

  - [ ]* 3.3 Write property test for registry version increment
    - **Property 5: Registry version increment**
    - **Validates: Requirements 2.3**

  - [ ]* 3.4 Write property test for registry listener notification
    - **Property 6: Registry listener notification**
    - **Validates: Requirements 2.4**

  - [ ]* 3.5 Write property test for registry write serialization
    - **Property 7: Registry write serialization**
    - **Validates: Requirements 15.2**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Storage Adapter interface and Mode A implementation
  - [x] 5.1 Create StorageAdapter interface
    - Define methods: initializeCollection, updateCollectionSchema, query, getById, create, update, delete, isUnique
    - _Requirements: 9.1_

  - [x] 5.2 Implement PhysicalTableStorageAdapter (Mode A)
    - Implement table creation with columns matching field definitions
    - Implement SQL type mapping (FieldType → PostgreSQL types)
    - Implement CRUD operations using JdbcTemplate
    - Implement query building with WHERE, ORDER BY, LIMIT, OFFSET clauses
    - Implement filter condition building for all operators (eq, neq, gt, lt, gte, lte, isnull, contains, starts, ends, icontains, istarts, iends, ieq)
    - _Requirements: 9.2, 9.4, 9.7_

  - [ ]* 5.3 Write property test for Mode A table creation
    - **Property 21: Mode A table creation**
    - **Validates: Requirements 9.4**

  - [x] 5.4 Implement SchemaMigrationEngine
    - Implement migration history tracking table
    - Implement schema diff detection (added fields, removed fields, type changes)
    - Implement ALTER TABLE generation for adding columns
    - Implement column deprecation (mark but don't drop)
    - Implement type change validation
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [ ]* 5.5 Write property test for schema migration - add field
    - **Property 22: Mode A schema migration - add field**
    - **Validates: Requirements 10.2**

  - [ ]* 5.6 Write property test for schema migration - remove field
    - **Property 23: Mode A schema migration - remove field**
    - **Validates: Requirements 10.3**

  - [ ]* 5.7 Write property test for schema migration - type change validation
    - **Property 24: Mode A schema migration - type change validation**
    - **Validates: Requirements 10.4**

  - [ ]* 5.8 Write property test for migration history tracking
    - **Property 25: Migration history tracking**
    - **Validates: Requirements 10.5**

- [x] 6. Implement Mode B storage adapter (JSONB)
  - [x] 6.1 Implement JsonbStorageAdapter
    - Implement shared table creation (emf_collections with JSONB column)
    - Implement JSONB query building with data->> operators
    - Implement CRUD operations storing data as JSONB
    - _Requirements: 9.3, 9.5_

  - [ ]* 6.2 Write property test for Mode B shared table storage
    - **Property 26: Mode B shared table storage**
    - **Validates: Requirements 9.5**

- [x] 7. Implement Validation Engine
  - [x] 7.1 Create ValidationEngine interface and DefaultValidationEngine implementation
    - Implement validation for all constraint types (min/max value, min/max length, pattern, nullable, immutable, unique, enum, reference)
    - Implement type validation (value matches expected FieldType)
    - Return ValidationResult with field-level errors
    - Integrate with StorageAdapter for unique and reference checks
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.10, 15.4_

  - [ ]* 7.2 Write property test for validation rule enforcement
    - **Property 27: Validation rule enforcement**
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.10**

  - [ ]* 7.3 Write property test for nullable constraint enforcement
    - **Property 28: Nullable constraint enforcement**
    - **Validates: Requirements 11.5**

  - [ ]* 7.4 Write property test for immutable constraint enforcement
    - **Property 29: Immutable constraint enforcement**
    - **Validates: Requirements 11.6**

  - [ ]* 7.5 Write property test for unique constraint enforcement
    - **Property 30: Unique constraint enforcement**
    - **Validates: Requirements 11.7**

  - [ ]* 7.6 Write property test for enum constraint enforcement
    - **Property 31: Enum constraint enforcement**
    - **Validates: Requirements 11.8**

  - [ ]* 7.7 Write property test for reference constraint enforcement
    - **Property 32: Reference constraint enforcement**
    - **Validates: Requirements 11.9**

  - [ ]* 7.8 Write property test for validation engine thread safety
    - **Property 33: Validation engine thread safety**
    - **Validates: Requirements 15.4**

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement Query Engine
  - [x] 9.1 Create query request models (QueryRequest, Pagination, SortField, FilterCondition, QueryResult)
    - Implement QueryRequest.fromParams() to parse query parameters
    - Implement pagination with defaults (page size 20)
    - Implement sort field parsing (handle "-" prefix for descending)
    - Implement filter parsing (filter[field][op]=value pattern)
    - _Requirements: 4.1, 4.2, 4.3, 5.1, 5.2, 6.1, 7.1-7.15_

  - [x] 9.2 Create QueryEngine interface and DefaultQueryEngine implementation
    - Implement executeQuery with validation of sort/filter/field parameters
    - Implement getById, create, update, delete operations
    - Integrate with ValidationEngine for create/update operations
    - Integrate with StorageAdapter for persistence
    - Add system fields (id, createdAt, updatedAt) automatically
    - _Requirements: 3.1, 4.1, 4.2, 4.3, 4.4, 5.1-5.5, 6.1-6.4, 7.1-7.15, 15.3_

  - [ ]* 9.3 Write property test for pagination correctness
    - **Property 11: Pagination correctness**
    - **Validates: Requirements 4.1, 4.2**

  - [ ]* 9.4 Write property test for pagination metadata completeness
    - **Property 12: Pagination metadata completeness**
    - **Validates: Requirements 4.4**

  - [ ]* 9.5 Write property test for sort order correctness
    - **Property 13: Sort order correctness**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [ ]* 9.6 Write property test for sort field validation
    - **Property 14: Sort field validation**
    - **Validates: Requirements 5.4**

  - [ ]* 9.7 Write property test for field selection filtering
    - **Property 15: Field selection filtering**
    - **Validates: Requirements 6.1, 6.4**

  - [ ]* 9.8 Write property test for field selection validation
    - **Property 16: Field selection validation**
    - **Validates: Requirements 6.3**

  - [ ]* 9.9 Write property test for comparison filter correctness
    - **Property 17: Comparison filter correctness**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6**

  - [ ]* 9.10 Write property test for string filter correctness
    - **Property 18: String filter correctness**
    - **Validates: Requirements 7.8, 7.9, 7.10, 7.11, 7.12, 7.13, 7.14**

  - [ ]* 9.11 Write property test for null filter correctness
    - **Property 19: Null filter correctness**
    - **Validates: Requirements 7.7**

  - [ ]* 9.12 Write property test for multiple filter AND logic
    - **Property 20: Multiple filter AND logic**
    - **Validates: Requirements 7.15**

  - [ ]* 9.13 Write property test for query engine concurrent execution
    - **Property 39: Query engine concurrent execution**
    - **Validates: Requirements 15.3**

- [x] 10. Implement Event Publishing
  - [x] 10.1 Create EventPublisher interface and KafkaEventPublisher implementation
    - Implement CollectionEvent record with factory methods (create, update, delete)
    - Implement Kafka publishing with KafkaTemplate
    - Implement topic name building (prefix.collectionName.eventType)
    - Handle event hook failures gracefully (log but don't fail operation)
    - Make event publishing conditional on eventsConfig.enabled
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [ ]* 10.2 Write property test for lifecycle event publishing
    - **Property 34: Lifecycle event publishing**
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.6**

  - [ ]* 10.3 Write property test for event hook failure isolation
    - **Property 35: Event hook failure isolation**
    - **Validates: Requirements 12.5**

- [x] 11. Implement Dynamic Router
  - [x] 11.1 Create DynamicCollectionRouter REST controller
    - Implement @GetMapping for list (with query parameters)
    - Implement @GetMapping for get by ID
    - Implement @PostMapping for create
    - Implement @PutMapping for update
    - Implement @DeleteMapping for delete
    - Extract collection name from path variable
    - Return 404 when collection not found in registry
    - Integrate with QueryEngine and ValidationEngine
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 11.2 Write property test for router collection name extraction
    - **Property 8: Router collection name extraction**
    - **Validates: Requirements 3.4**

  - [ ]* 11.3 Write property test for router 404 for missing collections
    - **Property 9: Router 404 for missing collections**
    - **Validates: Requirements 3.3**

  - [ ]* 11.4 Write property test for router passes collection definition to handler
    - **Property 10: Router passes collection definition to handler**
    - **Validates: Requirements 3.5**

- [x] 12. Implement error handling and HTTP status mapping
  - [x] 12.1 Create error response models and exception handlers
    - Implement ErrorResponse and FieldError records
    - Create @ControllerAdvice for global exception handling
    - Map validation errors to HTTP 400 with field-level details
    - Map not found to HTTP 404
    - Map conflicts (unique violations) to HTTP 409
    - Map server errors to HTTP 500 with generic message
    - Include request ID in all error responses
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_

  - [ ]* 12.2 Write property test for validation error response structure
    - **Property 36: Validation error response structure**
    - **Validates: Requirements 14.1, 14.5**

  - [ ]* 12.3 Write property test for not found error response
    - **Property 37: Not found error response**
    - **Validates: Requirements 14.2, 14.5**

  - [ ]* 12.4 Write property test for conflict error response
    - **Property 38: Conflict error response**
    - **Validates: Requirements 14.4, 14.5**

- [x] 13. Implement Spring configuration and auto-configuration
  - [x] 13.1 Create Spring Boot auto-configuration classes
    - Create @Configuration classes for registry, storage adapters, query engine, validation engine, event publisher
    - Use @ConditionalOnProperty for Mode A vs Mode B storage selection
    - Configure connection pooling for JdbcTemplate
    - Configure KafkaTemplate with appropriate serializers
    - Set up Spring Actuator endpoints for health checks
    - _Requirements: 15.5_

  - [x] 13.2 Create application.properties with sensible defaults
    - Configure default storage mode (PHYSICAL_TABLES)
    - Configure default page size (20)
    - Configure Kafka topic prefix
    - Configure database connection pool settings
    - _Requirements: All_

- [x] 14. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Integration testing and end-to-end validation
  - [x] 15.1 Write integration tests using Testcontainers
    - Set up PostgreSQL, Kafka, and Redis containers
    - Test full flow: create collection → create record → query → update → delete
    - Test schema migration: add field → verify table altered
    - Test concurrent access scenarios
    - Test event publishing to Kafka

  - [x] 15.2 Write example usage documentation
    - Create example showing how to use the runtime library
    - Document builder patterns for collection definitions
    - Document query parameter formats
    - Document storage adapter configuration

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property tests validate universal correctness properties with minimum 100 iterations
- Unit tests validate specific examples and edge cases
- Integration tests validate component interactions and end-to-end flows
- The implementation follows a bottom-up approach: models → registry → storage → validation → query → routing → events
- Checkpoints ensure incremental validation and provide opportunities for user feedback
