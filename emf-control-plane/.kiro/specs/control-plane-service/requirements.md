# Requirements Document: Control Plane Service

## Introduction

The Control Plane Service is the central configuration management service for the EMF (Enterprise Microservice Framework) platform. It provides REST APIs for managing collection definitions, authorization policies, UI configuration, OIDC providers, packages, and schema migrations. The service stores all configuration in PostgreSQL and publishes changes to Kafka for consumption by domain services.

## Glossary

- **Control_Plane**: The central service that manages all runtime configuration for the EMF platform
- **Collection**: A logical grouping of data entities with defined fields and operations
- **Field**: A typed attribute within a collection (e.g., string, number, reference)
- **Collection_Version**: An immutable snapshot of a collection's schema at a point in time
- **Authorization_Policy**: A rule defining who can access what resources and operations
- **OIDC_Provider**: An OpenID Connect identity provider configuration
- **Package**: A portable bundle of configuration that can be exported and imported
- **Migration_Run**: An execution of schema changes with tracking and rollback support
- **Domain_Service**: A microservice that consumes configuration from the control plane
- **Bootstrap_Config**: Initial configuration data loaded by the UI on startup
- **JWKS**: JSON Web Key Set used for JWT signature verification
- **Kafka_Event**: A message published to Kafka when configuration changes occur

## Requirements

### Requirement 1: Collection Management

**User Story:** As a platform administrator, I want to manage collection definitions through REST APIs, so that I can define the data structures for my domain services.

#### Acceptance Criteria

1. WHEN a client requests all collections, THE Control_Plane SHALL return a paginated list with filtering and sorting support
2. WHEN a client creates a new collection with valid data, THE Control_Plane SHALL persist it and return the created collection with a generated ID
3. WHEN a client creates a collection with invalid data, THE Control_Plane SHALL reject the request and return validation errors
4. WHEN a client requests a collection by ID, THE Control_Plane SHALL return the collection if it exists
5. WHEN a client requests a non-existent collection, THE Control_Plane SHALL return a 404 error
6. WHEN a client updates a collection, THE Control_Plane SHALL create a new Collection_Version and preserve the previous version
7. WHEN a client deletes a collection, THE Control_Plane SHALL soft-delete it by marking it as inactive
8. WHEN a collection is created or updated, THE Control_Plane SHALL publish a config.collection.changed event to Kafka

### Requirement 2: Field Management

**User Story:** As a platform administrator, I want to manage fields within collections, so that I can define the schema for my data entities.

#### Acceptance Criteria

1. WHEN a client requests all fields for a collection, THE Control_Plane SHALL return the list of active fields
2. WHEN a client adds a field to a collection, THE Control_Plane SHALL create a new Collection_Version with the added field
3. WHEN a client adds a field with invalid type or constraints, THE Control_Plane SHALL reject the request with validation errors
4. WHEN a client updates a field, THE Control_Plane SHALL create a new Collection_Version with the updated field
5. WHEN a client deletes a field, THE Control_Plane SHALL create a new Collection_Version marking the field as inactive
6. WHEN a field is added, updated, or deleted, THE Control_Plane SHALL publish a config.collection.changed event to Kafka

### Requirement 3: Authorization Management

**User Story:** As a platform administrator, I want to configure role-based access control, so that I can secure my collections and operations.

#### Acceptance Criteria

1. WHEN a client requests all roles, THE Control_Plane SHALL return the list of defined roles
2. WHEN a client creates a role with valid data, THE Control_Plane SHALL persist it and return the created role
3. WHEN a client requests all policies, THE Control_Plane SHALL return the list of authorization policies
4. WHEN a client creates a policy with valid data, THE Control_Plane SHALL persist it and return the created policy
5. WHEN a client sets route authorization for a collection, THE Control_Plane SHALL persist the route policies
6. WHEN a client sets field authorization for a collection, THE Control_Plane SHALL persist the field policies
7. WHEN authorization configuration changes, THE Control_Plane SHALL publish a config.authz.changed event to Kafka

### Requirement 4: OIDC Provider Management

**User Story:** As a platform administrator, I want to configure allowed identity providers, so that I can control which authentication sources are trusted.

#### Acceptance Criteria

1. WHEN a client requests all OIDC providers, THE Control_Plane SHALL return the list of configured providers
2. WHEN a client adds an OIDC provider with valid configuration, THE Control_Plane SHALL persist it and return the created provider
3. WHEN a client adds an OIDC provider with invalid configuration, THE Control_Plane SHALL reject the request with validation errors
4. WHEN a client updates an OIDC provider, THE Control_Plane SHALL persist the changes
5. WHEN a client deletes an OIDC provider, THE Control_Plane SHALL mark it as inactive
6. WHEN OIDC configuration changes, THE Control_Plane SHALL publish a config.oidc.changed event to Kafka
7. THE Control_Plane SHALL cache JWKS keys from OIDC providers for JWT validation

### Requirement 5: UI Configuration Management

**User Story:** As a platform administrator, I want to configure UI pages and menus, so that I can customize the admin interface for my users.

#### Acceptance Criteria

1. WHEN the UI requests bootstrap configuration, THE Control_Plane SHALL return the initial configuration including pages and menus
2. WHEN a client requests all UI pages, THE Control_Plane SHALL return the list of configured pages
3. WHEN a client creates a UI page with valid data, THE Control_Plane SHALL persist it and return the created page
4. WHEN a client updates a UI page, THE Control_Plane SHALL persist the changes
5. WHEN a client requests all UI menus, THE Control_Plane SHALL return the list of configured menus
6. WHEN a client updates a UI menu, THE Control_Plane SHALL persist the changes
7. WHEN UI configuration changes, THE Control_Plane SHALL publish a config.ui.changed event to Kafka

### Requirement 6: Package Management

**User Story:** As a platform administrator, I want to export and import configuration packages, so that I can promote configuration between environments.

#### Acceptance Criteria

1. WHEN a client requests a package export, THE Control_Plane SHALL generate a package containing selected configuration items
2. WHEN a client imports a package with dry-run enabled, THE Control_Plane SHALL validate the package and return a preview of changes without applying them
3. WHEN a client imports a package without dry-run, THE Control_Plane SHALL apply the configuration changes and return the import results
4. WHEN a package import fails validation, THE Control_Plane SHALL reject the import and return validation errors
5. THE Control_Plane SHALL track package exports and imports in the database

### Requirement 7: Migration Management

**User Story:** As a platform administrator, I want to plan and execute schema migrations, so that I can evolve my data structures safely.

#### Acceptance Criteria

1. WHEN a client requests a migration plan, THE Control_Plane SHALL generate a plan showing the steps required to migrate from the current schema to the target schema
2. WHEN a client requests all migration runs, THE Control_Plane SHALL return the history of executed migrations
3. WHEN a client requests a specific migration run, THE Control_Plane SHALL return the details including all steps and their status
4. THE Control_Plane SHALL track each migration step with success/failure status

### Requirement 8: Resource Discovery

**User Story:** As a domain service or API consumer, I want to discover available collections and their schemas, so that I can interact with the platform dynamically.

#### Acceptance Criteria

1. WHEN a client requests the resource discovery endpoint, THE Control_Plane SHALL return all active collections with their schemas
2. THE Control_Plane SHALL include field definitions, types, and constraints for each collection
3. THE Control_Plane SHALL include available operations for each collection
4. THE Control_Plane SHALL include authorization hints for each collection and field

### Requirement 9: OpenAPI Specification Generation

**User Story:** As an API consumer, I want to access a dynamically generated OpenAPI specification, so that I can understand and interact with the control plane APIs.

#### Acceptance Criteria

1. WHEN a client requests the OpenAPI specification in JSON format, THE Control_Plane SHALL return a valid OpenAPI 3.1 document
2. WHEN a client requests the OpenAPI specification in YAML format, THE Control_Plane SHALL return a valid OpenAPI 3.1 document
3. THE Control_Plane SHALL include all static endpoints in the OpenAPI specification
4. THE Control_Plane SHALL include dynamically generated endpoints for runtime collections in the OpenAPI specification

### Requirement 10: Event Publishing

**User Story:** As a domain service, I want to receive configuration change notifications, so that I can update my runtime behavior without restart.

#### Acceptance Criteria

1. WHEN a collection is created, updated, or deleted, THE Control_Plane SHALL publish a config.collection.changed event to Kafka
2. WHEN authorization configuration changes, THE Control_Plane SHALL publish a config.authz.changed event to Kafka
3. WHEN UI configuration changes, THE Control_Plane SHALL publish a config.ui.changed event to Kafka
4. WHEN OIDC provider configuration changes, THE Control_Plane SHALL publish a config.oidc.changed event to Kafka
5. THE Control_Plane SHALL include the full changed entity in each event payload
6. THE Control_Plane SHALL include a correlation ID in each event for tracing

### Requirement 11: Data Persistence

**User Story:** As the control plane service, I want to persist all configuration in PostgreSQL, so that configuration survives service restarts.

#### Acceptance Criteria

1. THE Control_Plane SHALL store all collection definitions in the COLLECTION table
2. THE Control_Plane SHALL store all field definitions in the FIELD table
3. THE Control_Plane SHALL store collection version history in the COLLECTION_VERSION table
4. THE Control_Plane SHALL store field version history in the FIELD_VERSION table
5. THE Control_Plane SHALL store roles in the ROLE table
6. THE Control_Plane SHALL store policies in the POLICY table
7. THE Control_Plane SHALL store route policies in the ROUTE_POLICY table
8. THE Control_Plane SHALL store field policies in the FIELD_POLICY table
9. THE Control_Plane SHALL store UI pages in the UI_PAGE table
10. THE Control_Plane SHALL store UI page policies in the UI_PAGE_POLICY table
11. THE Control_Plane SHALL store UI menus in the UI_MENU table
12. THE Control_Plane SHALL store UI menu items in the UI_MENU_ITEM table
13. THE Control_Plane SHALL store UI menu item policies in the UI_MENU_ITEM_POLICY table
14. THE Control_Plane SHALL store OIDC providers in the OIDC_PROVIDER table
15. THE Control_Plane SHALL store packages in the PACKAGE table
16. THE Control_Plane SHALL store package items in the PACKAGE_ITEM table
17. THE Control_Plane SHALL store migration runs in the MIGRATION_RUN table
18. THE Control_Plane SHALL store migration steps in the MIGRATION_STEP table

### Requirement 12: Authentication and Authorization

**User Story:** As a platform administrator, I want all control plane endpoints to be secured, so that only authorized users can modify configuration.

#### Acceptance Criteria

1. WHEN a client makes a request without a valid JWT token, THE Control_Plane SHALL reject the request with a 401 error
2. WHEN a client makes a request with a valid JWT token, THE Control_Plane SHALL extract the user identity and roles
3. WHEN a client makes a request to an endpoint they are not authorized for, THE Control_Plane SHALL reject the request with a 403 error
4. THE Control_Plane SHALL validate JWT signatures using JWKS keys from configured OIDC providers
5. THE Control_Plane SHALL cache JWKS keys to minimize external requests

### Requirement 13: Observability

**User Story:** As a platform operator, I want comprehensive observability, so that I can monitor and troubleshoot the control plane service.

#### Acceptance Criteria

1. THE Control_Plane SHALL emit structured JSON logs with correlation IDs for all requests
2. THE Control_Plane SHALL include traceId, spanId, and requestId in all log entries
3. THE Control_Plane SHALL expose Prometheus metrics via the /actuator/metrics endpoint
4. THE Control_Plane SHALL emit OpenTelemetry traces for all requests
5. THE Control_Plane SHALL provide a liveness health check at /actuator/health/liveness
6. THE Control_Plane SHALL provide a readiness health check at /actuator/health/readiness
7. WHEN the database is unavailable, THE Control_Plane SHALL report unhealthy on the readiness check
8. WHEN Kafka is unavailable, THE Control_Plane SHALL report unhealthy on the readiness check
9. WHEN Redis is unavailable, THE Control_Plane SHALL report unhealthy on the readiness check

### Requirement 14: Caching

**User Story:** As a platform operator, I want frequently accessed data to be cached, so that the control plane can handle high request volumes efficiently.

#### Acceptance Criteria

1. THE Control_Plane SHALL cache collection definitions in Redis
2. THE Control_Plane SHALL cache JWKS keys in Redis
3. WHEN cached data is updated, THE Control_Plane SHALL invalidate the cache entry
4. WHEN Redis is unavailable, THE Control_Plane SHALL fall back to direct database queries

### Requirement 15: Versioning and Immutability

**User Story:** As a platform administrator, I want collection schema changes to be versioned, so that I can track the evolution of my data structures and support rollback.

#### Acceptance Criteria

1. WHEN a collection is created, THE Control_Plane SHALL create an initial Collection_Version with version number 1
2. WHEN a collection is updated, THE Control_Plane SHALL create a new Collection_Version with an incremented version number
3. WHEN a field is added, updated, or deleted, THE Control_Plane SHALL create a new Collection_Version
4. THE Control_Plane SHALL preserve all previous Collection_Version records
5. THE Control_Plane SHALL allow querying historical versions of a collection
