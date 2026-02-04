# Implementation Plan: Control Plane Service

## Overview

This plan implements the Control Plane Service as a Spring Boot application with REST APIs for managing EMF platform configuration. The implementation follows a layered architecture with clear separation between controllers, services, repositories, and event publishing. All configuration changes are versioned, persisted to PostgreSQL, and published to Kafka.

## Tasks

- [x] 1. Set up project structure and dependencies
  - Create Maven project with Spring Boot 3.2+ parent
  - Add dependencies: Spring Web, Spring Data JPA, Spring Kafka, Spring Data Redis, Spring Security OAuth2 Resource Server, Spring Actuator, PostgreSQL driver, springdoc-openapi, Micrometer, OpenTelemetry
  - Add emf-platform runtime-core library dependency
  - Configure application.yml with database, Kafka, Redis, and security settings
  - Set up logging configuration with JSON format
  - _Requirements: All_

- [x] 2. Implement domain entities and repositories
  - [x] 2.1 Create JPA entities for core domain model
    - Create Collection, Field, CollectionVersion, FieldVersion entities with JPA annotations
    - Create Role, Policy, RoutePolicy, FieldPolicy entities
    - Create OidcProvider, UiPage, UiMenu, UiMenuItem entities
    - Create Package, PackageItem, MigrationRun, MigrationStep entities
    - Add audit fields (createdAt, updatedAt) using @CreatedDate and @LastModifiedDate
    - _Requirements: 11.1-11.18_
  
  - [ ]* 2.2 Write property test for entity persistence
    - **Property 1: Collection creation persistence**
    - **Validates: Requirements 1.2, 1.4**
  
  - [x] 2.3 Create Spring Data JPA repositories
    - Create CollectionRepository, FieldRepository, CollectionVersionRepository
    - Create RoleRepository, PolicyRepository, RoutePolicyRepository, FieldPolicyRepository
    - Create OidcProviderRepository, UiPageRepository, UiMenuRepository
    - Create PackageRepository, MigrationRunRepository
    - Add custom query methods for filtering and sorting
    - _Requirements: 1.1, 3.1, 3.3, 4.1, 5.2, 5.5, 7.2_

- [x] 3. Implement collection management service and APIs
  - [x] 3.1 Create CollectionService with CRUD operations
    - Implement listCollections with pagination, filtering, and sorting
    - Implement createCollection with validation
    - Implement getCollection by ID
    - Implement updateCollection with versioning
    - Implement deleteCollection with soft delete
    - Implement version creation logic
    - _Requirements: 1.1, 1.2, 1.4, 1.6, 1.7_
  
  - [ ]* 3.2 Write property tests for collection operations
    - **Property 2: Collection creation validation**
    - **Property 3: Collection listing with pagination**
    - **Property 4: Collection filtering**
    - **Property 5: Collection sorting**
    - **Property 6: Collection soft deletion**
    - **Validates: Requirements 1.1, 1.3, 1.7**
  
  - [x] 3.3 Create CollectionController with REST endpoints
    - Implement GET /control/collections with pagination
    - Implement POST /control/collections
    - Implement GET /control/collections/{id}
    - Implement PUT /control/collections/{id}
    - Implement DELETE /control/collections/{id}
    - Add @PreAuthorize annotations for ADMIN role
    - Add request/response DTOs with validation
    - _Requirements: 1.1, 1.2, 1.4, 1.6, 1.7_
  
  - [ ]* 3.4 Write unit tests for CollectionController
    - Test successful collection creation
    - Test validation errors
    - Test 404 for non-existent collection
    - Test pagination parameters
    - _Requirements: 1.2, 1.3, 1.4, 1.5_

- [x] 4. Implement field management service and APIs
  - [x] 4.1 Create FieldService with CRUD operations
    - Implement listFields for a collection
    - Implement addField with validation and versioning
    - Implement updateField with versioning
    - Implement deleteField with soft delete and versioning
    - _Requirements: 2.1, 2.2, 2.4, 2.5_
  
  - [ ]* 4.2 Write property tests for field operations
    - **Property 7: Field creation persistence**
    - **Property 8: Field creation validation**
    - **Property 9: Active fields only in listing**
    - **Property 27: Version creation on field addition**
    - **Property 28: Version creation on field update**
    - **Property 29: Version creation on field deletion**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
  
  - [x] 4.3 Add field endpoints to CollectionController
    - Implement GET /control/collections/{id}/fields
    - Implement POST /control/collections/{id}/fields
    - Implement PUT /control/collections/{id}/fields/{fieldId}
    - Implement DELETE /control/collections/{id}/fields/{fieldId}
    - Add request/response DTOs with validation
    - _Requirements: 2.1, 2.2, 2.4, 2.5_

- [x] 5. Implement versioning system
  - [x] 5.1 Create version management logic in CollectionService
    - Implement createNewVersion method
    - Implement version increment logic
    - Implement version preservation logic
    - Implement historical version query methods
    - _Requirements: 15.1, 15.2, 15.4, 15.5_
  
  - [ ]* 5.2 Write property tests for versioning
    - **Property 25: Initial version creation**
    - **Property 26: Version increment on collection update**
    - **Property 30: Version preservation**
    - **Validates: Requirements 15.1, 15.2, 15.4, 15.5**

- [x] 6. Checkpoint - Ensure collection and field management tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement authorization management service and APIs
  - [x] 7.1 Create AuthorizationService
    - Implement listRoles
    - Implement createRole
    - Implement listPolicies
    - Implement createPolicy
    - Implement setCollectionAuthorization (route and field policies)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  
  - [ ]* 7.2 Write property tests for authorization operations
    - **Property 10: Role creation persistence**
    - **Property 11: Policy creation persistence**
    - **Property 12: Route authorization persistence**
    - **Property 13: Field authorization persistence**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
  
  - [x] 7.3 Create AuthorizationController
    - Implement GET /control/roles
    - Implement POST /control/roles
    - Implement GET /control/policies
    - Implement POST /control/policies
    - Implement PUT /control/collections/{id}/authz
    - Add @PreAuthorize annotations for ADMIN role
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 8. Implement OIDC provider management service and APIs
  - [x] 8.1 Create OidcProviderService
    - Implement listProviders
    - Implement addProvider with validation
    - Implement updateProvider
    - Implement deleteProvider with soft delete
    - _Requirements: 4.1, 4.2, 4.4, 4.5_
  
  - [ ]* 8.2 Write property tests for OIDC provider operations
    - **Property 14: OIDC provider creation persistence**
    - **Property 15: OIDC provider validation**
    - **Property 16: OIDC provider update persistence**
    - **Property 17: OIDC provider soft deletion**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**
  
  - [x] 8.3 Create OidcProviderController
    - Implement GET /control/oidc/providers
    - Implement POST /control/oidc/providers
    - Implement PUT /control/oidc/providers/{id}
    - Implement DELETE /control/oidc/providers/{id}
    - Add @PreAuthorize annotations for ADMIN role
    - _Requirements: 4.1, 4.2, 4.4, 4.5_

- [x] 9. Implement UI configuration management service and APIs
  - [x] 9.1 Create UiConfigService
    - Implement getBootstrapConfig
    - Implement listPages
    - Implement createPage
    - Implement updatePage
    - Implement listMenus
    - Implement updateMenu
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_
  
  - [ ]* 9.2 Write property tests for UI configuration operations
    - **Property 18: UI page creation persistence**
    - **Property 19: UI page update persistence**
    - **Property 20: UI menu update persistence**
    - **Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6**
  
  - [x] 9.3 Create UiConfigController
    - Implement GET /ui/config/bootstrap
    - Implement GET /ui/pages
    - Implement POST /ui/pages
    - Implement PUT /ui/pages/{id}
    - Implement GET /ui/menus
    - Implement PUT /ui/menus/{id}
    - Add @PreAuthorize annotations for ADMIN role
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

- [x] 10. Checkpoint - Ensure authorization, OIDC, and UI management tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement Kafka event publishing
  - [x] 11.1 Create ConfigEventPublisher component
    - Implement publishCollectionChanged
    - Implement publishAuthzChanged
    - Implement publishUiChanged
    - Implement publishOidcChanged
    - Add correlation ID generation
    - Add event payload building with full entity
    - Configure Kafka topics and serialization
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  
  - [ ]* 11.2 Write property tests for event publishing
    - **Property 21: Collection change events**
    - **Property 22: Authorization change events**
    - **Property 23: UI configuration change events**
    - **Property 24: OIDC configuration change events**
    - **Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 10.6**
  
  - [x] 11.3 Integrate event publishing into services
    - Add event publishing to CollectionService
    - Add event publishing to AuthorizationService
    - Add event publishing to UiConfigService
    - Add event publishing to OidcProviderService
    - _Requirements: 1.8, 2.6, 3.7, 4.6, 5.7_

- [x] 12. Implement caching layer
  - [x] 12.1 Create cache configuration
    - Configure Redis connection
    - Configure cache managers for collections and JWKS
    - Set cache TTL and eviction policies
    - _Requirements: 14.1, 14.2_
  
  - [x] 12.2 Create JwksCache component
    - Implement getJwkSet with caching
    - Implement invalidate method
    - Implement fetchJwkSet from OIDC provider
    - Add fallback to direct fetch when Redis unavailable
    - _Requirements: 4.7, 12.5_
  
  - [ ]* 12.3 Write property tests for caching
    - **Property 43: JWKS key caching**
    - **Property 44: Collection definition caching**
    - **Property 45: Cache invalidation on update**
    - **Property 46: Redis fallback**
    - **Validates: Requirements 4.7, 14.1, 14.3, 14.4**
  
  - [x] 12.4 Add caching to CollectionService
    - Add @Cacheable to getCollection
    - Add @CacheEvict to updateCollection and deleteCollection
    - Implement fallback logic when Redis unavailable
    - _Requirements: 14.1, 14.3, 14.4_

- [x] 13. Implement security configuration
  - [x] 13.1 Create SecurityConfig
    - Configure SecurityFilterChain with JWT authentication
    - Configure OAuth2 Resource Server
    - Create JwtAuthenticationConverter for role extraction
    - Permit health check and OpenAPI endpoints
    - Require authentication for all other endpoints
    - _Requirements: 12.1, 12.2, 12.3_
  
  - [ ]* 13.2 Write property tests for security
    - **Property 39: Authentication rejection**
    - **Property 40: Token processing**
    - **Property 41: Authorization rejection**
    - **Property 42: JWT signature validation**
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4**
  
  - [x] 13.3 Integrate JwksCache with JWT validation
    - Configure JWT decoder to use JwksCache
    - Add JWKS key rotation support
    - _Requirements: 12.4, 12.5_

- [x] 14. Implement package management service and APIs
  - [x] 14.1 Create PackageService
    - Implement exportPackage with item selection
    - Implement importPackage with dry-run support
    - Implement validatePackage
    - Implement previewImport
    - Implement applyImport
    - Add package tracking to database
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_
  
  - [ ]* 14.2 Write property tests for package operations
    - **Property 31: Package export completeness**
    - **Property 32: Package import dry-run**
    - **Property 33: Package import application**
    - **Property 34: Package import validation**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**
  
  - [x] 14.3 Create PackageController
    - Implement POST /control/packages/export
    - Implement POST /control/packages/import with dryRun parameter
    - Add @PreAuthorize annotations for ADMIN role
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 15. Implement migration management service and APIs
  - [x] 15.1 Create MigrationService
    - Implement planMigration with step generation
    - Implement listMigrationRuns
    - Implement getMigrationRun
    - Implement generateSteps logic
    - Add migration run tracking to database
    - _Requirements: 7.1, 7.2, 7.3, 7.4_
  
  - [ ]* 15.2 Write property tests for migration operations
    - **Property 35: Migration plan generation**
    - **Property 36: Migration run tracking**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4**
  
  - [x] 15.3 Create MigrationController
    - Implement POST /control/migrations/plan
    - Implement GET /control/migrations/runs
    - Implement GET /control/migrations/runs/{id}
    - Add @PreAuthorize annotations for ADMIN role
    - _Requirements: 7.1, 7.2, 7.3_

- [x] 16. Checkpoint - Ensure event publishing, caching, security, package, and migration tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 17. Implement resource discovery service and API
  - [x] 17.1 Create DiscoveryService
    - Implement discoverResources
    - Implement buildResourceMetadata for collections
    - Include field definitions, types, constraints
    - Include available operations
    - Include authorization hints
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  
  - [ ]* 17.2 Write property test for resource discovery
    - **Property 37: Resource discovery completeness**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**
  
  - [x] 17.3 Create DiscoveryController
    - Implement GET /api/_meta/resources
    - _Requirements: 8.1_

- [x] 18. Implement OpenAPI specification generation
  - [x] 18.1 Create OpenApiCustomizer
    - Implement customise method to add dynamic paths
    - Implement addCollectionPaths for runtime collections
    - Generate path items for CRUD operations
    - Generate schemas for collection DTOs
    - _Requirements: 9.3, 9.4_
  
  - [ ]* 18.2 Write property test for OpenAPI generation
    - **Property 38: OpenAPI specification validity**
    - **Validates: Requirements 9.1, 9.2, 9.3, 9.4**
  
  - [x] 18.3 Configure springdoc-openapi
    - Add OpenAPI info (title, version, description)
    - Configure security scheme for JWT
    - Enable JSON and YAML endpoints
    - _Requirements: 9.1, 9.2_

- [x] 19. Implement observability features
  - [x] 19.1 Create LoggingFilter
    - Add correlation IDs (requestId, traceId, spanId) to MDC
    - Clear MDC after request completion
    - _Requirements: 13.1, 13.2_
  
  - [x] 19.2 Configure metrics and tracing
    - Create MetricsConfig with common tags
    - Configure OpenTelemetry tracing
    - Add custom metrics for business operations
    - _Requirements: 13.3, 13.4_
  
  - [ ]* 19.3 Write property tests for observability
    - **Property 47: Structured logging**
    - **Property 48: OpenTelemetry tracing**
    - **Validates: Requirements 13.1, 13.2, 13.4**
  
  - [x] 19.4 Configure health checks
    - Configure liveness health check
    - Configure readiness health check with DB, Kafka, Redis indicators
    - _Requirements: 13.5, 13.6, 13.7, 13.8, 13.9_
  
  - [ ]* 19.5 Write property test for health check dependency failures
    - **Property 49: Health check dependency failures**
    - **Validates: Requirements 13.7, 13.8, 13.9**

- [x] 20. Create database migration scripts
  - Create Flyway or Liquibase migration scripts for all tables
  - Add indexes for common query patterns
  - Add foreign key constraints
  - Add check constraints for data integrity
  - _Requirements: 11.1-11.18_

- [x] 21. Create Docker and Helm deployment artifacts
  - Create Dockerfile for control plane service
  - Create Helm chart in emf-helm repository
  - Configure environment variables for database, Kafka, Redis
  - Configure resource limits and health checks
  - Add ConfigMap and Secret templates
  - _Requirements: All_

- [x] 22. Final checkpoint - Run full test suite and integration tests
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (minimum 100 iterations each)
- Unit tests validate specific examples and edge cases
- Integration tests use Testcontainers for PostgreSQL, Kafka, and Redis
- All property tests must be tagged with: `@Tag("Feature: control-plane-service, Property {number}: {property_text}")`
