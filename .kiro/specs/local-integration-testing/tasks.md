# Implementation Plan: Local Integration Testing

## Overview

This implementation plan breaks down the local integration testing suite into discrete, incremental tasks. The approach follows a bottom-up strategy: first establishing the infrastructure and sample service, then building the test framework, and finally implementing comprehensive test suites. Each task builds on previous work and includes validation through tests.

**Key Architecture Decision**: The sample service uses the **emf-platform/runtime-core** library for collection management. This provides:
- Automatic table creation via `StorageAdapter`
- Automatic REST API via `DynamicCollectionRouter`
- Built-in validation, pagination, sorting, filtering
- Event-driven architecture with `CollectionEvent`
- No need for JPA entities, repositories, or custom controllers

See `INTEGRATION_WITH_EMF_PLATFORM.md` for detailed architecture information.

## Tasks

- [x] 1. Extend Docker Compose configuration for platform services
  - Add emf-control-plane service definition with health checks
  - Add emf-gateway service definition with health checks
  - Add sample-service service definition with health checks
  - Configure service dependencies and startup order
  - Test that all services start successfully
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.10_

- [x] 2. Update database initialization scripts
  - [x] 2.1 Update docker/postgres/init.sql
    - Ensure database exists
    - Enable UUID extension
    - Add comment that runtime-core will create collection tables automatically
    - _Requirements: 1.1_
  
  - [x] 2.2 Verify automatic table creation
    - Test that StorageAdapter.initializeCollection() creates tables
    - Verify projects and tasks tables are created on startup
    - Verify foreign key constraints are created
    - _Requirements: 2.1, 2.2, 2.3_

- [x] 3. Create Keycloak realm configuration for test users
  - [x] 3.1 Create emf-realm.json with test users
    - Define admin user with ADMIN and USER roles
    - Define regular user with USER role
    - Define guest user with no roles
    - Configure emf-client for password grant flow
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  
  - [x] 3.2 Mount realm configuration in docker-compose.yml
    - Update keycloak service volume mount
    - Test that realm is imported on startup
    - _Requirements: 1.4_

- [x] 4. Implement Sample Service
  - [x] 4.1 Create Spring Boot project structure
    - Set up Maven build configuration
    - Add dependency on emf-platform/runtime-core
    - Add dependencies: Spring Boot, Spring Data Redis
    - Create application.yml with integration-test profile
    - Configure emf.storage.mode=PHYSICAL_TABLES
    - _Requirements: 2.1, 2.2_
  
  - [x] 4.2 Implement CollectionInitializer component
    - Define projects collection using CollectionDefinitionBuilder
    - Define tasks collection using CollectionDefinitionBuilder
    - Register collections in CollectionRegistry
    - Initialize storage using StorageAdapter
    - _Requirements: 2.1, 2.2, 2.3_
  
  - [x] 4.3 Implement service registration with Control Plane
    - Register service on ApplicationReadyEvent
    - Register projects collection with control plane
    - Register tasks collection with control plane
    - Extract relationships from collection definitions
    - _Requirements: 2.4_
  
  - [x] 4.4 Verify DynamicCollectionRouter is auto-configured
    - Confirm EmfRuntimeAutoConfiguration creates router bean
    - Test that CRUD endpoints are available at /api/collections/{name}
    - Verify JSON:API response format
    - _Requirements: 2.5, 2.6_
  
  - [x] 4.5 Implement ResourceCacheService
    - Create cacheResource method with key pattern "jsonapi:{type}:{id}"
    - Create getCachedResource method
    - Create invalidateResource method
    - Set TTL to 10 minutes for cached resources
    - _Requirements: 2.8_
  
  - [x] 4.6 Implement CacheEventListener
    - Listen for CollectionEvent from runtime-core
    - Cache resources on CREATE and UPDATE events
    - Invalidate cache on DELETE events
    - _Requirements: 2.8_
  
  - [x] 4.7 Implement EnhancedCollectionRouter
    - Extend DynamicCollectionRouter
    - Override GET /{collectionName}/{id} to add include support
    - Parse include query parameter
    - Fetch related resources from Redis cache
    - Add related resources to included array
    - Handle cache misses gracefully
    - Handle invalid include parameters
    - _Requirements: 2.7_
  
  - [x] 4.8 Verify request validation
    - Test that ValidationEngine validates required fields
    - Verify HTTP 400 for missing required fields
    - Verify HTTP 400 for invalid field types
    - Verify errors in JSON:API error format
    - _Requirements: 2.9, 2.10_
  
  - [x] 4.9 Create Dockerfile for Sample Service
    - Multi-stage build with Maven
    - Use eclipse-temurin:21-jre as base
    - Expose port 8080
    - Add health check endpoint
    - _Requirements: 1.7_

- [x] 5. Checkpoint - Verify Sample Service
  - Start Docker environment with sample service
  - Verify service health check passes
  - Verify service registers with control plane
  - Verify database tables are created
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement Test Framework Base Classes
  - [x] 6.1 Create IntegrationTestBase abstract class
    - Define service URLs as constants
    - Implement waitForServices method using Awaitility
    - Implement isServiceHealthy helper method
    - Add setUp and tearDown lifecycle methods
    - _Requirements: 14.4_
  
  - [x] 6.2 Create AuthenticationHelper component
    - Implement getAdminToken method
    - Implement getUserToken method
    - Implement getToken method with Keycloak integration
    - Implement createAuthHeaders helper method
    - _Requirements: 5.7, 5.8_
  
  - [x] 6.3 Create TestDataHelper component
    - Implement createProject method (POST to /api/collections/projects)
    - Implement createTask method (POST to /api/collections/tasks)
    - Implement deleteProject method (DELETE to /api/collections/projects/{id})
    - Implement deleteTask method (DELETE to /api/collections/tasks/{id})
    - Track created resources for cleanup
    - _Requirements: 3.7, 3.8, 15.1, 15.2_

- [-] 7. Implement Collection CRUD Integration Tests
  - [x] 7.1 Create CollectionCrudIntegrationTest class
    - Extend IntegrationTestBase
    - Inject AuthenticationHelper and TestDataHelper
    - Implement cleanup in tearDown method
    - _Requirements: 9.1-9.10_
  
  - [ ]* 7.2 Write property test for resource creation
    - **Property 20: Resource ID Uniqueness**
    - **Validates: Requirements 9.2**
  
  - [ ]* 7.3 Write property test for CRUD persistence
    - **Property 21: CRUD Persistence**
    - **Validates: Requirements 9.3, 9.7, 9.9**
  
  - [ ]* 7.4 Write property test for non-existent resources
    - **Property 22: Non-Existent Resource Handling**
    - **Validates: Requirements 9.10**
  
  - [ ]* 7.5 Write unit tests for CRUD operations
    - Test creating a project with valid data
    - Test reading a project by ID
    - Test updating a project
    - Test deleting a project
    - Test listing projects with pagination
    - _Requirements: 9.1, 9.4, 9.5, 9.6, 9.8_

- [x] 8. Implement Authentication Integration Tests
  - [x] 8.1 Create AuthenticationIntegrationTest class
    - Extend IntegrationTestBase
    - Inject AuthenticationHelper
    - _Requirements: 5.1-5.8_
  
  - [ ]* 8.2 Write property test for authentication validation
    - **Property 10: Authentication Token Validation**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  
  - [ ]* 8.3 Write property test for valid token acceptance
    - **Property 11: Valid Token Acceptance**
    - **Validates: Requirements 5.4, 5.5, 5.6**
  
  - [ ]* 8.4 Write unit tests for token acquisition
    - Test getting admin token from Keycloak
    - Test getting user token from Keycloak
    - Test token contains correct claims
    - _Requirements: 5.7, 5.8_

- [-] 9. Implement Authorization Integration Tests
  - [x] 9.1 Create AuthorizationIntegrationTest class
    - Extend IntegrationTestBase
    - Inject AuthenticationHelper and TestDataHelper
    - Set up test authorization policies
    - _Requirements: 6.1-6.8_
  
  - [ ]* 9.2 Write property test for route authorization
    - **Property 12: Route Authorization Enforcement**
    - **Validates: Requirements 6.1, 6.2**
  
  - [ ]* 9.3 Write property test for field-level authorization
    - **Property 13: Field-Level Authorization**
    - **Validates: Requirements 6.3, 6.4, 6.7**
  
  - [ ]* 9.4 Write property test for dynamic authorization updates
    - **Property 14: Dynamic Authorization Updates**
    - **Validates: Requirements 6.8, 12.7**
  
  - [ ]* 9.5 Write unit tests for authorization scenarios
    - Test admin can access admin-only route
    - Test user cannot access admin-only route
    - Test field visibility changes based on role
    - _Requirements: 6.5, 6.6, 6.7_

- [x] 10. Checkpoint - Verify Authentication and Authorization
  - Run authentication tests
  - Run authorization tests
  - Verify all policies are enforced correctly
  - Ensure all tests pass, ask the user if questions arise.

- [-] 11. Implement Related Collections Integration Tests
  - [x] 11.1 Create RelatedCollectionsIntegrationTest class
    - Extend IntegrationTestBase
    - Inject TestDataHelper
    - _Requirements: 7.1-7.7_
  
  - [ ]* 11.2 Write property test for relationship persistence
    - **Property 15: Relationship Persistence**
    - **Validates: Requirements 7.2, 7.3**
  
  - [ ]* 11.3 Write property test for relationship integrity
    - **Property 16: Relationship Integrity**
    - **Validates: Requirements 7.6**
  
  - [ ]* 11.4 Write unit tests for relationship operations
    - Test creating task with project relationship
    - Test reading task with relationship data
    - Test updating task relationship
    - Test deleting resource with relationships
    - Test querying by relationship filters
    - _Requirements: 7.1, 7.4, 7.5, 7.7_

- [x] 12. Implement Include Parameter Integration Tests
  - [x] 12.1 Create IncludeParameterIntegrationTest class
    - Extend IntegrationTestBase
    - Inject TestDataHelper
    - _Requirements: 8.1-8.8_
  
  - [ ]* 12.2 Write property test for include processing
    - **Property 17: Include Parameter Processing**
    - **Validates: Requirements 8.1, 8.3**
  
  - [ ]* 12.3 Write property test for cache miss handling
    - **Property 18: Cache Miss Handling**
    - **Validates: Requirements 8.6, 11.5**
  
  - [ ]* 12.4 Write property test for invalid include parameters
    - **Property 19: Invalid Include Parameter Handling**
    - **Validates: Requirements 8.8**
  
  - [ ]* 12.5 Write unit tests for include scenarios
    - Test include single relationship
    - Test include multiple relationships
    - Test nested includes
    - Test include with field filtering
    - _Requirements: 8.4, 8.5, 8.7_

- [-] 13. Implement Cache Integration Tests
  - [x] 13.1 Create CacheIntegrationTest class
    - Extend IntegrationTestBase
    - Inject TestDataHelper
    - Access Redis directly for verification
    - _Requirements: 11.1-11.7_
  
  - [ ]* 13.2 Write property test for resource caching
    - **Property 2: Resource Caching Consistency**
    - **Validates: Requirements 2.8, 8.2, 11.1, 11.2, 11.3**
  
  - [ ]* 13.3 Write property test for cache TTL
    - **Property 23: Cache TTL Configuration**
    - **Validates: Requirements 11.4**
  
  - [ ]* 13.4 Write property test for cache invalidation on update
    - **Property 24: Cache Invalidation on Update**
    - **Validates: Requirements 11.6**
  
  - [ ]* 13.5 Write property test for cache invalidation on delete
    - **Property 25: Cache Invalidation on Delete**
    - **Validates: Requirements 11.7**

- [-] 14. Implement Event-Driven Configuration Tests
  - [x] 14.1 Create ConfigurationUpdateIntegrationTest class
    - Extend IntegrationTestBase
    - Set up Kafka consumer for verification
    - _Requirements: 12.1-12.8_
  
  - [ ]* 14.2 Write property test for event publishing
    - **Property 6: Configuration Event Publishing**
    - **Validates: Requirements 4.2, 12.1, 12.3, 12.5**
  
  - [ ]* 14.3 Write property test for event processing
    - **Property 7: Configuration Event Processing**
    - **Validates: Requirements 4.3, 12.2, 12.4, 12.6**
  
  - [ ]* 14.4 Write property test for malformed event handling
    - **Property 26: Malformed Event Handling**
    - **Validates: Requirements 12.8**
  
  - [ ]* 14.5 Write unit tests for configuration updates
    - Test collection change event is published
    - Test gateway receives and processes collection change
    - Test authorization policy change event is published
    - Test gateway receives and processes authorization change
    - Test service change event is published
    - Test gateway receives and processes service change
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

- [x] 15. Checkpoint - Verify Event-Driven Configuration
  - Run configuration update tests
  - Verify events are published to Kafka
  - Verify gateway processes events correctly
  - Ensure all tests pass, ask the user if questions arise.

- [-] 16. Implement Collection Management Tests
  - [x] 16.1 Create CollectionManagementIntegrationTest class
    - Extend IntegrationTestBase
    - Test control plane collection API
    - _Requirements: 4.1-4.7_
  
  - [ ]* 16.2 Write property test for collection persistence
    - **Property 5: Collection Persistence**
    - **Validates: Requirements 4.1**
  
  - [ ]* 16.3 Write property test for request routing
    - **Property 8: Request Routing Correctness**
    - **Validates: Requirements 4.4**
  
  - [ ]* 16.4 Write property test for invalid collection rejection
    - **Property 9: Invalid Collection Rejection**
    - **Validates: Requirements 4.7**
  
  - [ ]* 16.5 Write unit tests for collection management
    - Test creating collection via control plane
    - Test collection is stored in database
    - Test collection change event is published
    - Test gateway creates route for collection
    - Test querying collection via gateway
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [-] 17. Implement Error Handling Tests
  - [x] 17.1 Create ErrorHandlingIntegrationTest class
    - Extend IntegrationTestBase
    - Test various error scenarios
    - _Requirements: 13.1-13.8_
  
  - [ ]* 17.2 Write property test for validation errors
    - **Property 3: Required Field Validation**
    - **Property 4: Invalid Data Rejection**
    - **Validates: Requirements 2.9, 2.10, 13.1, 13.2, 13.3, 13.8**
  
  - [ ]* 17.3 Write property test for backend error propagation
    - **Property 27: Backend Error Propagation**
    - **Validates: Requirements 13.4**
  
  - [ ]* 17.4 Write property test for infrastructure failure resilience
    - **Property 28: Infrastructure Failure Resilience**
    - **Validates: Requirements 13.5, 13.6, 13.7**
  
  - [ ]* 17.5 Write unit tests for error scenarios
    - Test invalid JSON returns HTTP 400
    - Test missing required fields returns HTTP 400
    - Test invalid field types returns HTTP 400
    - Test backend error is propagated
    - Test database failure is handled gracefully
    - Test Redis failure is handled gracefully
    - Test Kafka failure is handled gracefully
    - Test error responses follow JSON:API format
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8_

- [x] 18. Implement End-to-End Flow Tests
  - [x] 18.1 Create EndToEndFlowIntegrationTest class
    - Extend IntegrationTestBase
    - Inject all helper components
    - _Requirements: 10.1-10.8_
  
  - [ ]* 18.2 Write property test for JSON:API format compliance
    - **Property 1: JSON:API Response Format Compliance**
    - **Validates: Requirements 2.6**
  
  - [ ]* 18.3 Write unit tests for complete flows
    - Test complete project lifecycle (create → read → update → delete)
    - Test project with tasks lifecycle (create project → create tasks → read with includes → update → delete)
    - Test authentication flow (get token → make request)
    - Test authorization flow (admin access → user denied)
    - Test error handling flow (invalid request → error response)
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8_

- [x] 19. Create Test Execution Scripts
  - [x] 19.1 Create run-integration-tests.sh script
    - Start Docker environment
    - Wait for all services to be healthy
    - Run all integration tests
    - Generate test report
    - Clean up Docker environment
    - _Requirements: 14.1, 14.2, 14.5, 14.8_
  
  - [x] 19.2 Create wait-for-services.sh script
    - Check health of all services
    - Retry with timeout
    - Exit with error if services don't start
    - _Requirements: 14.4_
  
  - [x] 19.3 Add support for test categories
    - Add --category flag to run specific tests
    - Support categories: infrastructure, authentication, authorization, crud, relationships, includes, cache, events, errors, e2e
    - _Requirements: 14.2_
  
  - [x] 19.4 Add support for watch mode
    - Add --watch flag for development
    - Re-run tests on file changes
    - _Requirements: 14.3_
  
  - [x] 19.5 Add support for CI mode
    - Add --ci flag for CI/CD pipelines
    - Generate JUnit XML reports
    - Collect logs on failure
    - _Requirements: 14.7_

- [x] 20. Create Documentation
  - [x] 20.1 Create README.md for integration tests
    - Document prerequisites
    - Document setup instructions
    - Document how to run tests
    - Document how to add new tests
    - _Requirements: 16.1, 16.2, 16.3_
  
  - [x] 20.2 Create ARCHITECTURE.md
    - Document test environment architecture
    - Include Mermaid diagrams
    - Document component interactions
    - _Requirements: 16.5, 16.8_
  
  - [x] 20.3 Create SAMPLE-SERVICE-API.md
    - Document sample service endpoints
    - Document request/response formats
    - Include example requests
    - _Requirements: 16.6_
  
  - [x] 20.4 Create TROUBLESHOOTING.md
    - Document common issues
    - Document solutions
    - Include debugging commands
    - _Requirements: 16.7_
  
  - [x] 20.5 Create example test cases
    - Provide example for each test category
    - Include comments explaining approach
    - _Requirements: 16.4_

- [x] 21. Create CI/CD Integration
  - [x] 21.1 Create GitHub Actions workflow
    - Set up job for integration tests
    - Configure Docker caching
    - Run tests on push and pull request
    - Upload artifacts on failure
    - _Requirements: 14.7_
  
  - [x] 21.2 Add test reporting
    - Generate JUnit XML reports
    - Generate HTML reports
    - Publish test results to GitHub
    - _Requirements: 14.5, 14.6_
  
  - [x] 21.3 Add performance monitoring
    - Track test execution time
    - Alert on performance degradation
    - _Requirements: 17.1_

- [x] 22. Final Checkpoint - Complete Integration Test Suite
  - Run full test suite
  - Verify all tests pass
  - Verify test execution time is under 5 minutes
  - Verify all documentation is complete
  - Verify CI/CD pipeline works
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- The sample service is the foundation for all integration tests
- Test framework provides reusable utilities for all test suites
- Documentation ensures the test suite is maintainable and extensible
