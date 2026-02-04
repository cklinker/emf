# Requirements Document: Local Integration Testing

## Introduction

This document specifies the requirements for a comprehensive local integration testing suite for the EMF (Enterprise Microservice Framework) platform. The test suite will provide an automated, reproducible environment for testing the complete platform stack including infrastructure services (PostgreSQL, Redis, Kafka), core platform services (Control Plane, API Gateway), and sample domain services. The suite will validate end-to-end functionality including collection management, authentication, authorization, JSON:API features, and CRUD operations.

## Glossary

- **Test_Suite**: The complete set of integration tests and supporting infrastructure
- **Test_Environment**: The Docker-based environment containing all required services
- **Control_Plane**: The EMF control plane service that manages collections, services, and authorization
- **Gateway**: The API Gateway service that routes requests and enforces policies
- **Sample_Service**: A test domain service that implements JSON:API collections for testing
- **Collection**: A JSON:API resource type with fields and relationships
- **Related_Collection**: A collection that has a relationship to another collection
- **Include**: A JSON:API query parameter for embedding related resources
- **CRUD**: Create, Read, Update, Delete operations
- **Authorization_Policy**: Rules that control access to routes and fields
- **Test_Data**: Sample data used for testing collection operations
- **Docker_Compose**: Tool for defining and running multi-container Docker applications
- **Integration_Test**: An automated test that validates multiple components working together
- **Bootstrap**: The initial configuration loading process when services start

## Requirements

### Requirement 1: Docker Environment Setup

**User Story:** As a developer, I want a complete Docker environment with all required services, so that I can run integration tests locally without manual setup.

#### Acceptance Criteria

1. THE Test_Environment SHALL include a PostgreSQL container with the EMF control plane database schema
2. THE Test_Environment SHALL include a Redis container for caching and rate limiting
3. THE Test_Environment SHALL include a Kafka container in KRaft mode for configuration events
4. THE Test_Environment SHALL include a Keycloak container for OIDC authentication
5. THE Test_Environment SHALL include the Control_Plane service container
6. THE Test_Environment SHALL include the Gateway service container
7. THE Test_Environment SHALL include a Sample_Service container for testing
8. WHEN the Test_Environment starts, THE Test_Environment SHALL wait for all service health checks to pass before running tests
9. WHEN a service fails to start, THE Test_Environment SHALL report which service failed and provide logs
10. THE Test_Environment SHALL use a dedicated Docker network for service communication

### Requirement 2: Sample Service Implementation

**User Story:** As a developer, I want a sample service with realistic collections and relationships, so that I can test all JSON:API features.

#### Acceptance Criteria

1. THE Sample_Service SHALL implement a "projects" collection with fields: name, description, status, created_at
2. THE Sample_Service SHALL implement a "tasks" collection with fields: title, description, completed, project_id
3. THE Sample_Service SHALL implement a relationship from tasks to projects
4. THE Sample_Service SHALL register its collections with the Control_Plane on startup
5. THE Sample_Service SHALL implement all CRUD operations for both collections
6. THE Sample_Service SHALL return responses in JSON:API format
7. THE Sample_Service SHALL support JSON:API include parameter for related resources
8. THE Sample_Service SHALL cache resources in Redis for include processing
9. WHEN the Sample_Service receives a create request, THE Sample_Service SHALL validate required fields
10. WHEN the Sample_Service receives invalid data, THE Sample_Service SHALL return HTTP 400 with error details

### Requirement 3: Test Data Management

**User Story:** As a developer, I want test data to be automatically seeded, so that tests have consistent starting state.

#### Acceptance Criteria

1. WHEN the Test_Environment starts, THE Test_Suite SHALL seed the database with test collections
2. WHEN the Test_Environment starts, THE Test_Suite SHALL seed the database with test authorization policies
3. WHEN the Test_Environment starts, THE Test_Suite SHALL create test users in Keycloak with different roles
4. THE Test_Suite SHALL create at least one user with "admin" role
5. THE Test_Suite SHALL create at least one user with "user" role
6. THE Test_Suite SHALL create at least one user with no special roles
7. WHEN tests complete, THE Test_Suite SHALL clean up test data to ensure test isolation
8. THE Test_Suite SHALL provide helper functions for creating Test_Data during tests

### Requirement 4: Collection Creation Tests

**User Story:** As a developer, I want to test collection creation through the control plane, so that I can verify collection management works correctly.

#### Acceptance Criteria

1. WHEN a test creates a collection via the Control_Plane API, THE Control_Plane SHALL store the collection in the database
2. WHEN a collection is created, THE Control_Plane SHALL publish a collection changed event to Kafka
3. WHEN the Gateway receives the collection changed event, THE Gateway SHALL create a route for the collection
4. WHEN a test queries the collection via the Gateway, THE Gateway SHALL route the request to the correct service
5. THE Test_Suite SHALL verify that collections can be created with custom field definitions
6. THE Test_Suite SHALL verify that collections can be created with relationships
7. THE Test_Suite SHALL verify that invalid collection definitions are rejected with appropriate errors

### Requirement 5: Authentication Tests

**User Story:** As a developer, I want to test authentication flows, so that I can verify JWT validation works correctly.

#### Acceptance Criteria

1. THE Test_Suite SHALL test that requests without JWT tokens are rejected with HTTP 401
2. THE Test_Suite SHALL test that requests with invalid JWT tokens are rejected with HTTP 401
3. THE Test_Suite SHALL test that requests with expired JWT tokens are rejected with HTTP 401
4. THE Test_Suite SHALL test that requests with valid JWT tokens are accepted
5. THE Test_Suite SHALL verify that user identity is correctly extracted from JWT tokens
6. THE Test_Suite SHALL verify that user roles are correctly extracted from JWT tokens
7. THE Test_Suite SHALL test token acquisition from Keycloak using client credentials flow
8. THE Test_Suite SHALL test token acquisition from Keycloak using password flow

### Requirement 6: Authorization Tests

**User Story:** As a developer, I want to test authorization policies, so that I can verify access control works correctly.

#### Acceptance Criteria

1. THE Test_Suite SHALL test that route policies correctly allow authorized users
2. THE Test_Suite SHALL test that route policies correctly deny unauthorized users with HTTP 403
3. THE Test_Suite SHALL test that field policies correctly filter fields from responses
4. THE Test_Suite SHALL test that field policies apply to both primary data and included resources
5. THE Test_Suite SHALL verify that users with admin role can access admin-only routes
6. THE Test_Suite SHALL verify that users without admin role cannot access admin-only routes
7. THE Test_Suite SHALL verify that field visibility changes based on user roles
8. THE Test_Suite SHALL test that authorization policies can be updated dynamically via Kafka events

### Requirement 7: Related Collections Tests

**User Story:** As a developer, I want to test related collections, so that I can verify relationship handling works correctly.

#### Acceptance Criteria

1. THE Test_Suite SHALL test creating a resource with a relationship to another resource
2. THE Test_Suite SHALL verify that relationship data is correctly stored in the database
3. THE Test_Suite SHALL verify that relationship data is correctly returned in JSON:API responses
4. THE Test_Suite SHALL test updating relationships on existing resources
5. THE Test_Suite SHALL test deleting resources that have relationships
6. THE Test_Suite SHALL verify that relationship integrity is maintained
7. THE Test_Suite SHALL test querying resources by relationship filters

### Requirement 8: Include Parameter Tests

**User Story:** As a developer, I want to test JSON:API include functionality, so that I can verify related resource embedding works correctly.

#### Acceptance Criteria

1. THE Test_Suite SHALL test that include parameter correctly embeds related resources
2. THE Test_Suite SHALL verify that included resources are fetched from Redis cache
3. THE Test_Suite SHALL verify that included resources are correctly formatted in the included array
4. THE Test_Suite SHALL test multiple include parameters (comma-separated)
5. THE Test_Suite SHALL test nested includes (dot-separated relationship paths)
6. THE Test_Suite SHALL verify that missing related resources are handled gracefully
7. THE Test_Suite SHALL verify that field policies apply to included resources
8. THE Test_Suite SHALL test that invalid include parameters are ignored

### Requirement 9: CRUD Operation Tests

**User Story:** As a developer, I want to test all CRUD operations on collections, so that I can verify complete data lifecycle management.

#### Acceptance Criteria

1. THE Test_Suite SHALL test creating resources via POST requests
2. THE Test_Suite SHALL verify that created resources are assigned unique IDs
3. THE Test_Suite SHALL verify that created resources are stored in the database
4. THE Test_Suite SHALL test reading individual resources via GET requests
5. THE Test_Suite SHALL test reading resource collections via GET requests with pagination
6. THE Test_Suite SHALL test updating resources via PATCH requests
7. THE Test_Suite SHALL verify that updates are persisted to the database
8. THE Test_Suite SHALL test deleting resources via DELETE requests
9. THE Test_Suite SHALL verify that deleted resources are removed from the database
10. THE Test_Suite SHALL test that operations on non-existent resources return HTTP 404

### Requirement 10: End-to-End Request Flow Tests

**User Story:** As a developer, I want to test complete request flows through all components, so that I can verify the entire platform works together.

#### Acceptance Criteria

1. THE Test_Suite SHALL test a complete flow: authenticate, create resource, read resource, update resource, delete resource
2. THE Test_Suite SHALL verify that requests pass through Gateway authentication
3. THE Test_Suite SHALL verify that requests pass through Gateway authorization
4. THE Test_Suite SHALL verify that requests are routed to the correct backend service
5. THE Test_Suite SHALL verify that responses are processed by Gateway filters
6. THE Test_Suite SHALL verify that field filtering is applied to responses
7. THE Test_Suite SHALL verify that include processing is applied to responses
8. THE Test_Suite SHALL test error handling at each stage of the request flow

### Requirement 11: Cache Integration Tests

**User Story:** As a developer, I want to test Redis cache integration, so that I can verify caching works correctly.

#### Acceptance Criteria

1. THE Test_Suite SHALL test that resources are cached in Redis after creation
2. THE Test_Suite SHALL verify that cached resources use the correct key pattern "jsonapi:{type}:{id}"
3. THE Test_Suite SHALL test that include processing retrieves resources from cache
4. THE Test_Suite SHALL test that cache entries have appropriate TTL values
5. THE Test_Suite SHALL test that cache misses are handled gracefully
6. THE Test_Suite SHALL test that cache invalidation works when resources are updated
7. THE Test_Suite SHALL test that cache invalidation works when resources are deleted

### Requirement 12: Event-Driven Configuration Tests

**User Story:** As a developer, I want to test Kafka-based configuration updates, so that I can verify dynamic reconfiguration works correctly.

#### Acceptance Criteria

1. THE Test_Suite SHALL test that collection changes are published to Kafka
2. THE Test_Suite SHALL verify that the Gateway receives and processes collection change events
3. THE Test_Suite SHALL test that authorization policy changes are published to Kafka
4. THE Test_Suite SHALL verify that the Gateway receives and processes authorization change events
5. THE Test_Suite SHALL test that service changes are published to Kafka
6. THE Test_Suite SHALL verify that the Gateway receives and processes service change events
7. THE Test_Suite SHALL verify that configuration changes take effect without restarting services
8. THE Test_Suite SHALL test that malformed Kafka events are handled gracefully

### Requirement 13: Error Handling Tests

**User Story:** As a developer, I want to test error scenarios, so that I can verify the platform handles failures gracefully.

#### Acceptance Criteria

1. THE Test_Suite SHALL test that invalid JSON payloads return HTTP 400 with error details
2. THE Test_Suite SHALL test that missing required fields return HTTP 400 with error details
3. THE Test_Suite SHALL test that invalid field types return HTTP 400 with error details
4. THE Test_Suite SHALL test that backend service errors are propagated correctly
5. THE Test_Suite SHALL test that database connection failures are handled gracefully
6. THE Test_Suite SHALL test that Redis connection failures are handled gracefully
7. THE Test_Suite SHALL test that Kafka connection failures are handled gracefully
8. THE Test_Suite SHALL verify that error responses follow JSON:API error format

### Requirement 14: Test Execution Framework

**User Story:** As a developer, I want a simple command to run all integration tests, so that I can easily validate the platform.

#### Acceptance Criteria

1. THE Test_Suite SHALL provide a single command to start the Test_Environment and run all tests
2. THE Test_Suite SHALL provide a command to run specific test categories
3. THE Test_Suite SHALL provide a command to run tests in watch mode for development
4. WHEN tests start, THE Test_Suite SHALL verify that all services are healthy before running tests
5. WHEN tests complete, THE Test_Suite SHALL generate a test report with pass/fail status
6. WHEN tests fail, THE Test_Suite SHALL provide detailed error messages and logs
7. THE Test_Suite SHALL support running tests in CI/CD pipelines
8. THE Test_Suite SHALL clean up Docker containers after tests complete

### Requirement 15: Test Isolation and Cleanup

**User Story:** As a developer, I want tests to be isolated from each other, so that test failures don't cascade.

#### Acceptance Criteria

1. WHEN a test creates resources, THE Test_Suite SHALL clean up those resources after the test completes
2. WHEN a test modifies configuration, THE Test_Suite SHALL restore the original configuration after the test completes
3. THE Test_Suite SHALL use unique identifiers for test resources to avoid conflicts
4. THE Test_Suite SHALL support running tests in parallel without interference
5. WHEN the Test_Environment is stopped, THE Test_Suite SHALL remove all Docker volumes
6. THE Test_Suite SHALL provide hooks for test setup and teardown
7. THE Test_Suite SHALL ensure database state is reset between test runs

### Requirement 16: Documentation and Examples

**User Story:** As a developer, I want clear documentation and examples, so that I can understand how to run and extend the tests.

#### Acceptance Criteria

1. THE Test_Suite SHALL include a README with setup instructions
2. THE Test_Suite SHALL include a README with instructions for running tests
3. THE Test_Suite SHALL include a README with instructions for adding new tests
4. THE Test_Suite SHALL include example test cases for each test category
5. THE Test_Suite SHALL document the Test_Environment architecture
6. THE Test_Suite SHALL document the Sample_Service API
7. THE Test_Suite SHALL include troubleshooting guide for common issues
8. THE Test_Suite SHALL include diagrams showing the test architecture

### Requirement 17: Performance and Reliability

**User Story:** As a developer, I want tests to run quickly and reliably, so that I can iterate efficiently.

#### Acceptance Criteria

1. THE Test_Suite SHALL complete all tests in under 5 minutes on standard developer hardware
2. THE Test_Suite SHALL retry flaky operations (service startup, network calls) up to 3 times
3. THE Test_Suite SHALL use health checks to ensure services are ready before running tests
4. THE Test_Suite SHALL use appropriate timeouts for all network operations
5. THE Test_Suite SHALL provide progress indicators during test execution
6. THE Test_Suite SHALL support running a subset of tests for faster feedback
7. THE Test_Suite SHALL cache Docker images to speed up subsequent runs
