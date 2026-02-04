# Requirements Document: Platform Core Runtime

## Introduction

The Platform Core Runtime is the foundational runtime library for the EMF (Enterprise Microservice Framework). It provides the core abstractions and runtime engine that enables dynamic collection management, routing, query building, and storage adapters. This runtime allows teams to build enterprise-grade microservices with runtime-configurable resources (collections) that can be created and modified via UI and exposed as REST endpoints without requiring service restart.

## Glossary

- **Collection**: A runtime-defined resource type with fields, validation rules, storage configuration, API configuration, authorization configuration, and event configuration
- **Runtime_Registry**: A thread-safe in-memory registry that holds compiled collection definitions and supports atomic updates
- **Dynamic_Router**: A component that routes incoming HTTP requests to appropriate handlers based on runtime-registered collections
- **Query_Engine**: A component that builds and executes queries with support for pagination, sorting, filtering, and field selection
- **Storage_Adapter**: An extensible abstraction for persisting collection data to various storage backends
- **Validation_Engine**: A component that performs field-level validation based on collection definitions
- **Mode_A**: Physical Tables storage mode where each collection maps to a real PostgreSQL table
- **Mode_B**: JSONB document store mode where collections are stored in a single table with JSONB columns
- **Collection_Definition**: The in-memory representation of a collection including all its configuration
- **Field_Definition**: The specification of a single field within a collection including type, validation rules, and constraints

## Requirements

### Requirement 1: Collection Definition Model

**User Story:** As a platform developer, I want an in-memory representation of collection definitions, so that I can work with runtime-configured collections programmatically.

#### Acceptance Criteria

1. THE Collection_Definition SHALL contain field definitions, validation rules, storage mode configuration, API configuration, authorization configuration, and event configuration
2. THE Field_Definition SHALL specify field name, data type, validation constraints, nullability, immutability, uniqueness, enum values, and reference relationships
3. WHEN a Collection_Definition is created, THE System SHALL validate that all required properties are present
4. THE Collection_Definition SHALL support serialization to and from JSON format
5. THE Field_Definition SHALL support data types including string, integer, long, double, boolean, date, datetime, and JSON

### Requirement 2: Runtime Registry

**User Story:** As a platform developer, I want a thread-safe registry for collection definitions, so that I can safely update collections at runtime without service restart.

#### Acceptance Criteria

1. THE Runtime_Registry SHALL store collection definitions in a thread-safe manner
2. WHEN a collection definition is updated, THE Runtime_Registry SHALL perform atomic updates using copy-on-write semantics
3. THE Runtime_Registry SHALL track version numbers for each collection definition
4. WHEN a collection definition changes, THE Runtime_Registry SHALL notify registered listeners
5. THE Runtime_Registry SHALL support concurrent read operations without blocking
6. WHEN querying for a collection definition, THE Runtime_Registry SHALL return the definition within 10 milliseconds for 99% of requests

### Requirement 3: Dynamic Router

**User Story:** As a platform developer, I want dynamic HTTP routing based on runtime collections, so that new collections are immediately accessible via REST API without code changes.

#### Acceptance Criteria

1. WHEN an HTTP request arrives, THE Dynamic_Router SHALL route it to the appropriate handler based on the collection name in the URL path
2. THE Dynamic_Router SHALL support standard CRUD operations including LIST, GET, POST, PUT, PATCH, and DELETE
3. WHEN a collection is not found in the registry, THE Dynamic_Router SHALL return HTTP 404 with a descriptive error message
4. THE Dynamic_Router SHALL extract collection name from URL patterns matching `/api/collections/{collectionName}` and `/api/collections/{collectionName}/{id}`
5. WHEN routing a request, THE Dynamic_Router SHALL pass the collection definition to the handler

### Requirement 4: Query Engine - Pagination

**User Story:** As an API consumer, I want to paginate through large result sets, so that I can efficiently retrieve data without overwhelming the system.

#### Acceptance Criteria

1. WHEN a request includes `page[number]` parameter, THE Query_Engine SHALL return the specified page of results
2. WHEN a request includes `page[size]` parameter, THE Query_Engine SHALL limit results to the specified page size
3. THE Query_Engine SHALL default to page size of 20 when not specified
4. THE Query_Engine SHALL include pagination metadata in the response including total count, current page, page size, and total pages
5. WHEN page number exceeds available pages, THE Query_Engine SHALL return an empty result set with valid pagination metadata

### Requirement 5: Query Engine - Sorting

**User Story:** As an API consumer, I want to sort query results by one or more fields, so that I can retrieve data in a meaningful order.

#### Acceptance Criteria

1. WHEN a request includes `sort=field1` parameter, THE Query_Engine SHALL sort results by field1 in ascending order
2. WHEN a request includes `sort=-field1` parameter, THE Query_Engine SHALL sort results by field1 in descending order
3. WHEN a request includes `sort=field1,-field2` parameter, THE Query_Engine SHALL sort by field1 ascending then field2 descending
4. WHEN a sort field does not exist in the collection, THE Query_Engine SHALL return HTTP 400 with a descriptive error message
5. THE Query_Engine SHALL support sorting on all primitive field types

### Requirement 6: Query Engine - Field Selection

**User Story:** As an API consumer, I want to select specific fields to return, so that I can reduce payload size and improve performance.

#### Acceptance Criteria

1. WHEN a request includes `fields=fieldA,fieldB` parameter, THE Query_Engine SHALL return only the specified fields in the response
2. WHEN no fields parameter is provided, THE Query_Engine SHALL return all fields
3. WHEN a specified field does not exist, THE Query_Engine SHALL return HTTP 400 with a descriptive error message
4. THE Query_Engine SHALL always include the ID field in responses regardless of field selection

### Requirement 7: Query Engine - Filtering

**User Story:** As an API consumer, I want to filter query results using various operators, so that I can retrieve only the data I need.

#### Acceptance Criteria

1. WHEN a request includes `filter[field][eq]=value`, THE Query_Engine SHALL return records where field equals value
2. WHEN a request includes `filter[field][neq]=value`, THE Query_Engine SHALL return records where field does not equal value
3. WHEN a request includes `filter[field][gt]=value`, THE Query_Engine SHALL return records where field is greater than value
4. WHEN a request includes `filter[field][lt]=value`, THE Query_Engine SHALL return records where field is less than value
5. WHEN a request includes `filter[field][gte]=value`, THE Query_Engine SHALL return records where field is greater than or equal to value
6. WHEN a request includes `filter[field][lte]=value`, THE Query_Engine SHALL return records where field is less than or equal to value
7. WHEN a request includes `filter[field][isnull]=true`, THE Query_Engine SHALL return records where field is null
8. WHEN a request includes `filter[field][contains]=value`, THE Query_Engine SHALL return records where field contains value (case-sensitive)
9. WHEN a request includes `filter[field][starts]=value`, THE Query_Engine SHALL return records where field starts with value (case-sensitive)
10. WHEN a request includes `filter[field][ends]=value`, THE Query_Engine SHALL return records where field ends with value (case-sensitive)
11. WHEN a request includes `filter[field][icontains]=value`, THE Query_Engine SHALL return records where field contains value (case-insensitive)
12. WHEN a request includes `filter[field][istarts]=value`, THE Query_Engine SHALL return records where field starts with value (case-insensitive)
13. WHEN a request includes `filter[field][iends]=value`, THE Query_Engine SHALL return records where field ends with value (case-insensitive)
14. WHEN a request includes `filter[field][ieq]=value`, THE Query_Engine SHALL return records where field equals value (case-insensitive)
15. WHEN multiple filters are provided, THE Query_Engine SHALL combine them using AND logic

### Requirement 8: Query Engine - Optimization

**User Story:** As a platform developer, I want query optimization recommendations, so that I can improve database performance.

#### Acceptance Criteria

1. WHEN a query is executed, THE Query_Engine SHALL analyze the query for potential optimizations
2. WHEN a query would benefit from an index, THE Query_Engine SHALL log an index recommendation
3. THE Query_Engine SHALL track query execution times for performance monitoring

### Requirement 9: Storage Adapter Interface

**User Story:** As a platform developer, I want an extensible storage abstraction, so that I can support different storage backends and modes.

#### Acceptance Criteria

1. THE Storage_Adapter SHALL define interface methods for create, read, update, delete, and query operations
2. THE Storage_Adapter SHALL support Mode_A (Physical Tables) where each collection maps to a PostgreSQL table
3. THE Storage_Adapter SHALL support Mode_B (JSONB document store) where collections are stored in a single table with JSONB columns
4. WHEN a collection is created in Mode_A, THE Storage_Adapter SHALL create a corresponding database table with columns matching field definitions
5. WHEN a collection is created in Mode_B, THE Storage_Adapter SHALL store records in a shared table with JSONB columns
6. THE Storage_Adapter SHALL support custom adapter implementations via Service Provider Interface (SPI)
7. THE Storage_Adapter SHALL default to Mode_A (Physical Tables) when no mode is specified

### Requirement 10: Storage Adapter - Schema Migration

**User Story:** As a platform developer, I want automatic schema migrations for Mode A storage, so that collection changes are reflected in the database without manual intervention.

#### Acceptance Criteria

1. WHEN a collection definition is updated in Mode_A, THE Storage_Adapter SHALL generate and execute appropriate ALTER TABLE statements
2. WHEN a field is added to a collection, THE Storage_Adapter SHALL add the corresponding column to the table
3. WHEN a field is removed from a collection, THE Storage_Adapter SHALL mark the column as deprecated but not drop it
4. WHEN a field type is changed, THE Storage_Adapter SHALL validate that the change is compatible or return an error
5. THE Storage_Adapter SHALL maintain a migration history table tracking all schema changes

### Requirement 11: Validation Engine

**User Story:** As a platform developer, I want field-level validation based on collection definitions, so that data integrity is enforced automatically.

#### Acceptance Criteria

1. WHEN a field has a minimum value constraint, THE Validation_Engine SHALL reject values below the minimum
2. WHEN a field has a maximum value constraint, THE Validation_Engine SHALL reject values above the maximum
3. WHEN a field has a length constraint, THE Validation_Engine SHALL reject strings exceeding the maximum length
4. WHEN a field has a pattern constraint, THE Validation_Engine SHALL reject values not matching the regex pattern
5. WHEN a field is marked as non-nullable, THE Validation_Engine SHALL reject null values
6. WHEN a field is marked as immutable, THE Validation_Engine SHALL reject updates to that field after initial creation
7. WHEN a field is marked as unique, THE Validation_Engine SHALL reject duplicate values
8. WHEN a field has enum values defined, THE Validation_Engine SHALL reject values not in the enum list
9. WHEN a field references another collection, THE Validation_Engine SHALL verify the referenced record exists
10. WHEN validation fails, THE Validation_Engine SHALL return a descriptive error message indicating which field and constraint failed

### Requirement 12: Event Publishing Hooks

**User Story:** As a platform developer, I want integration points for event publishing, so that collection lifecycle events can be published to Kafka.

#### Acceptance Criteria

1. WHEN a record is created, THE System SHALL invoke registered event hooks with the create event
2. WHEN a record is updated, THE System SHALL invoke registered event hooks with the update event
3. WHEN a record is deleted, THE System SHALL invoke registered event hooks with the delete event
4. THE System SHALL support registering multiple event hooks per collection
5. WHEN an event hook fails, THE System SHALL log the error but not fail the operation
6. THE System SHALL include the collection name, operation type, record ID, and record data in event payloads

### Requirement 13: Configuration Model

**User Story:** As a platform developer, I want POJOs and builders for collection configuration, so that I can programmatically create and modify collection definitions.

#### Acceptance Criteria

1. THE System SHALL provide a builder pattern for constructing Collection_Definition objects
2. THE System SHALL provide a builder pattern for constructing Field_Definition objects
3. THE System SHALL validate required fields when building collection definitions
4. THE System SHALL support method chaining in builder implementations
5. THE System SHALL provide immutable collection and field definition objects after building

### Requirement 14: Error Handling

**User Story:** As an API consumer, I want clear error messages, so that I can understand and fix issues with my requests.

#### Acceptance Criteria

1. WHEN a validation error occurs, THE System SHALL return HTTP 400 with a JSON error response containing field-level error details
2. WHEN a resource is not found, THE System SHALL return HTTP 404 with a descriptive error message
3. WHEN a server error occurs, THE System SHALL return HTTP 500 with a generic error message and log detailed error information
4. WHEN a conflict occurs (e.g., unique constraint violation), THE System SHALL return HTTP 409 with a descriptive error message
5. THE System SHALL include a request ID in all error responses for traceability

### Requirement 15: Thread Safety and Concurrency

**User Story:** As a platform operator, I want the runtime to handle concurrent requests safely, so that the system remains stable under load.

#### Acceptance Criteria

1. THE Runtime_Registry SHALL support concurrent read operations without locking
2. THE Runtime_Registry SHALL serialize write operations to prevent race conditions
3. THE Query_Engine SHALL support concurrent query execution without shared mutable state
4. THE Validation_Engine SHALL be stateless and thread-safe
5. THE Storage_Adapter SHALL use connection pooling for database access
