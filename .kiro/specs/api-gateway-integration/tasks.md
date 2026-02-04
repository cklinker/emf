# Implementation Plan: API Gateway Integration

## Overview

This implementation plan breaks down the API Gateway feature into discrete coding tasks. The gateway will be built using Spring Cloud Gateway with Spring Boot, following a reactive architecture with WebFlux. Tasks are organized to build incrementally, with early validation through property-based tests using JUnit QuickCheck.

## Tasks

- [x] 1. Set up project structure and dependencies
  - Create Maven project with Spring Boot 3.x and Spring Cloud Gateway
  - Add dependencies: spring-cloud-starter-gateway, spring-boot-starter-data-redis-reactive, spring-kafka, spring-security-oauth2-resource-server, junit-quickcheck
  - Configure application.yml with placeholder properties for control plane URL, Kafka, Redis, and JWT issuer
  - Set up test directory structure (unit/ and property/ packages)
  - _Requirements: 13.1, 13.2, 13.3, 13.4_

- [x] 2. Implement route management components
  - [x] 2.1 Create RouteDefinition and RouteRegistry classes
    - Implement RouteDefinition with id, serviceId, path, backendUrl, collectionName, rateLimit fields
    - Implement RouteRegistry with ConcurrentHashMap for thread-safe route storage
    - Implement methods: addRoute, removeRoute, updateRoute, findByPath, getAllRoutes, clear
    - _Requirements: 1.5_
  
  - [ ]* 2.2 Write property test for route registry indexing
    - **Property 2: Route Registry Indexing**
    - Generate random RouteDefinition objects
    - Add routes to registry and verify retrieval by path pattern
    - Verify concurrent access doesn't corrupt registry
    - _Requirements: 1.5_
  
  - [x] 2.3 Create RouteConfigService for bootstrap configuration
    - Implement fetchBootstrapConfig() to call control plane /control/bootstrap endpoint
    - Parse bootstrap response into RouteDefinition objects
    - Validate required fields (serviceId, collectionId, path, backendUrl)
    - _Requirements: 1.1, 1.2_
  
  - [ ]* 2.4 Write property test for route configuration validation
    - **Property 1: Route Configuration Validation**
    - Generate random route configs (valid and invalid)
    - Verify valid configs are added to registry
    - Verify invalid configs are skipped with error logs
    - _Requirements: 1.2, 1.3, 1.4_
  
  - [x] 2.5 Implement DynamicRouteLocator
    - Extend AbstractRouteLocator from Spring Cloud Gateway
    - Implement getRoutes() to return Flux<Route> from RouteRegistry
    - Convert RouteDefinition to Spring Cloud Gateway Route objects
    - _Requirements: 1.3_

- [x] 3. Checkpoint - Verify route management
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement authentication filter
  - [x] 4.1 Create JwtAuthenticationFilter
    - Implement GlobalFilter with order -100
    - Extract Authorization header from request
    - Validate JWT using Spring Security ReactiveJwtDecoder
    - Store GatewayPrincipal in ServerWebExchange attributes
    - Return 401 for missing/invalid/expired tokens
    - Allow unauthenticated access to /control/bootstrap
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [x] 4.2 Create GatewayPrincipal and PrincipalExtractor classes
    - Implement GatewayPrincipal with username, roles, claims fields
    - Implement PrincipalExtractor to extract user info from JWT claims
    - Extract roles from "roles" or "authorities" claim
    - _Requirements: 3.6_
  
  - [ ]* 4.3 Write property test for JWT validation and extraction
    - **Property 5: JWT Validation and Extraction**
    - Generate random valid JWTs with various claims
    - Verify principal extraction succeeds with correct data
    - Generate invalid/expired JWTs and verify 401 response
    - _Requirements: 3.1, 3.3, 3.5, 3.6_
  
  - [ ]* 4.4 Write unit tests for authentication edge cases
    - Test missing Authorization header returns 401
    - Test expired token returns 401
    - Test bootstrap endpoint allows unauthenticated access
    - _Requirements: 3.2, 3.4_

- [x] 5. Implement authorization components
  - [x] 5.1 Create AuthzConfig and policy classes
    - Implement AuthzConfig with collectionId, routePolicies, fieldPolicies
    - Implement RoutePolicy with method, policyId, roles
    - Implement FieldPolicy with fieldName, policyId, roles
    - Implement AuthzConfigCache with ConcurrentHashMap storage
    - _Requirements: 4.1_
  
  - [x] 5.2 Create PolicyEvaluator
    - Implement evaluate() method to check if principal roles match policy roles
    - Implement hasRole() helper method
    - Support multiple roles (OR logic - principal needs any of the required roles)
    - _Requirements: 4.3, 4.6_
  
  - [x] 5.3 Implement RouteAuthorizationFilter
    - Implement GlobalFilter with order 0
    - Extract collection ID from route
    - Lookup route policies for collection and HTTP method
    - If no policy, allow request
    - If policy exists, evaluate against principal using PolicyEvaluator
    - Return 403 if principal doesn't satisfy policy
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [ ]* 5.4 Write property test for route policy evaluation
    - **Property 6: Route Policy Evaluation**
    - Generate random route policies and principals
    - Verify requests with matching roles proceed
    - Verify requests without matching roles return 403
    - _Requirements: 4.1, 4.3, 4.4, 4.5, 4.6_
  
  - [ ]* 5.5 Write property test for default allow behavior
    - **Property 7: Default Allow for Unpolicied Routes**
    - Generate random requests to routes without policies
    - Verify all requests proceed regardless of principal roles
    - _Requirements: 4.2_

- [x] 6. Checkpoint - Verify authentication and authorization
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement JSON:API processing components
  - [x] 7.1 Create JSON:API data models
    - Implement JsonApiDocument with data, included, meta, errors fields
    - Implement ResourceObject with type, id, attributes, relationships
    - Implement Relationship with data (ResourceIdentifier or List)
    - Implement ResourceIdentifier with type and id
    - _Requirements: 5.1, 6.2_
  
  - [x] 7.2 Create JsonApiParser
    - Implement parse() method using Jackson ObjectMapper
    - Parse JSON:API response body into JsonApiDocument
    - Handle both single resource and collection responses
    - Handle errors in response
    - _Requirements: 5.1_
  
  - [ ]* 7.3 Write property test for JSON:API parsing
    - **Property 8: JSON:API Response Parsing**
    - Generate random valid JSON:API responses
    - Verify parsing succeeds and produces correct structure
    - Test both single resources and collections
    - _Requirements: 5.1_
  
  - [x] 7.4 Implement FieldAuthorizationFilter
    - Implement GlobalFilter with order 100 (runs after backend response)
    - Parse JSON:API response body
    - Extract collection ID from response
    - Lookup field policies for collection
    - For each resource object (data and included):
      - For each field in attributes:
        - If field has policy, evaluate against principal
        - Remove field if principal doesn't satisfy policy
    - Rebuild response with filtered fields
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6_
  
  - [ ]* 7.5 Write property test for field policy filtering
    - **Property 9: Field Policy Filtering**
    - Generate random JSON:API responses with various fields
    - Generate random field policies
    - Verify fields without policies remain
    - Verify fields with unsatisfied policies are removed
    - Verify fields with satisfied policies remain
    - Verify filtering applies to both data and included
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6_

- [x] 8. Implement JSON:API include processing
  - [x] 8.1 Create IncludeResolver with Redis integration
    - Implement resolveIncludes() method
    - Parse include query parameter (comma-separated)
    - Extract relationships from primary data resources
    - For each relationship, build Redis key: "jsonapi:{type}:{id}"
    - Lookup resources in Redis using ReactiveRedisTemplate
    - Collect found resources, skip missing with logged cache miss
    - Return Mono<List<ResourceObject>>
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 7.2_
  
  - [x] 8.2 Create JsonApiIncludeFilter
    - Implement GlobalFilter with order 200 (runs after field filtering)
    - Check for include query parameter
    - If present, parse response and resolve includes using IncludeResolver
    - Apply field filtering to included resources
    - Build final response with included array
    - _Requirements: 6.1, 6.6_
  
  - [ ]* 8.3 Write property test for include parameter parsing
    - **Property 10: Include Parameter Parsing**
    - Generate random include parameters (valid and invalid names)
    - Verify valid names are parsed correctly
    - Verify invalid names are ignored
    - _Requirements: 6.1, 6.7_
  
  - [ ]* 8.4 Write property test for include resolution
    - **Property 11: Include Resolution from Cache**
    - Generate random JSON:API responses with relationships
    - Populate Redis with some related resources
    - Verify correct Redis keys are used ("jsonapi:{type}:{id}")
    - Verify found resources are added to included array
    - Verify missing resources are skipped with cache miss log
    - _Requirements: 6.2, 6.3, 6.4, 6.5, 7.2_
  
  - [ ]* 8.5 Write property test for included resource filtering
    - **Property 12: Field Filtering on Included Resources**
    - Generate random included resources with field policies
    - Verify field filtering applies to included resources
    - _Requirements: 6.6_
  
  - [ ]* 8.6 Write unit tests for Redis error handling
    - Test Redis connection failure logs error and continues
    - Test Redis deserialization of valid JSON
    - _Requirements: 7.3, 7.4_

- [x] 9. Checkpoint - Verify JSON:API processing
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement rate limiting
  - [x] 10.1 Create RateLimitConfig and RateLimitResult classes
    - Implement RateLimitConfig with requestsPerWindow and windowDuration
    - Implement RateLimitResult with allowed, remainingRequests, retryAfter
    - _Requirements: 8.1_
  
  - [x] 10.2 Create RedisRateLimiter service
    - Implement checkRateLimit() method
    - Build Redis key: "ratelimit:{routeId}:{principal}"
    - Use Redis INCR command to increment counter
    - If counter == 1, set TTL to window duration
    - If counter > limit, return not allowed with retry-after
    - If counter <= limit, return allowed with remaining count
    - Handle Redis unavailable gracefully (allow request, log warning)
    - _Requirements: 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_
  
  - [x] 10.3 Implement RateLimitFilter
    - Implement GlobalFilter with order -50 (after auth, before routing)
    - Extract route ID and principal from context
    - Check if route has rate limit config
    - If yes, call RedisRateLimiter.checkRateLimit()
    - If not allowed, return 429 with Retry-After header
    - If allowed, add rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset)
    - _Requirements: 8.2, 8.4, 8.5_
  
  - [ ]* 10.4 Write property test for rate limit enforcement
    - **Property 15: Rate Limit Enforcement**
    - Generate random rate limit configs and request sequences
    - Verify correct Redis key pattern is used
    - Verify requests within limit are allowed
    - Verify requests exceeding limit return 429
    - _Requirements: 8.2, 8.3, 8.4, 8.5_
  
  - [ ]* 10.5 Write property test for rate limit TTL
    - **Property 16: Rate Limit TTL**
    - Generate random rate limit configs
    - Verify TTL on Redis keys matches window duration
    - _Requirements: 8.6_
  
  - [ ]* 10.6 Write property test for rate limit graceful degradation
    - **Property 17: Rate Limit Graceful Degradation**
    - Simulate Redis unavailable
    - Verify requests are allowed and warning is logged
    - _Requirements: 8.7_

- [x] 11. Implement request routing and forwarding
  - [x] 11.1 Configure Spring Cloud Gateway routing
    - Configure RouteLocator bean to use DynamicRouteLocator
    - Configure default filters for all routes
    - Configure timeout settings (30 seconds)
    - _Requirements: 9.1, 9.2_
  
  - [x] 11.2 Create HeaderTransformationFilter
    - Implement GlobalFilter with order 50 (before forwarding)
    - Remove Authorization header from forwarded request
    - Add X-Forwarded-User header with principal username
    - Add X-Forwarded-Roles header with comma-separated roles
    - Preserve all other headers
    - _Requirements: 9.4, 9.5, 9.6_
  
  - [ ]* 11.3 Write property test for route matching
    - **Property 18: Route Matching**
    - Generate random requests and route patterns
    - Verify matching routes are forwarded to correct backend
    - Verify non-matching routes return 404
    - _Requirements: 9.1, 9.2, 9.3_
  
  - [ ]* 11.4 Write property test for header transformation
    - **Property 19: Header Transformation**
    - Generate random requests with various headers
    - Verify Authorization header is removed
    - Verify X-Forwarded-User and X-Forwarded-Roles are added
    - Verify other headers are preserved
    - _Requirements: 9.4, 9.5, 9.6_
  
  - [ ]* 11.5 Write property test for backend error passthrough
    - **Property 20: Backend Error Passthrough**
    - Generate random backend error responses
    - Verify errors are returned to client unchanged
    - _Requirements: 9.7_

- [x] 12. Implement control plane integration
  - [x] 12.1 Add control plane route configuration
    - Add static route for "/control/**" to control plane backend
    - Configure route to use same filters as other routes
    - Add exception for "/control/bootstrap" to skip authentication
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  
  - [ ]* 12.2 Write property test for control plane routing consistency
    - **Property 21: Control Plane Routing Consistency**
    - Generate random requests to control plane paths
    - Verify authentication and authorization apply (except bootstrap)
    - Verify bootstrap endpoint allows unauthenticated access
    - _Requirements: 10.2, 10.3_

- [x] 13. Checkpoint - Verify routing and forwarding
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Implement Kafka configuration listener
  - [x] 14.1 Create ConfigEvent and payload classes
    - Implement ConfigEvent<T> with eventId, eventType, correlationId, timestamp, payload
    - Implement CollectionChangedPayload matching control plane structure
    - Implement AuthzChangedPayload matching control plane structure
    - Implement ServiceChangedPayload matching control plane structure
    - _Requirements: 2.4, 2.5, 2.6_
  
  - [x] 14.2 Create ConfigEventListener
    - Implement @KafkaListener for collection-changed topic
    - Implement @KafkaListener for authz-changed topic
    - Implement @KafkaListener for service-changed topic
    - Parse events and update RouteRegistry or AuthzConfigCache
    - Handle service DELETED events by removing routes
    - Log configuration changes
    - Handle malformed events gracefully (log error, continue)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_
  
  - [ ]* 14.3 Write property test for configuration event processing
    - **Property 3: Configuration Event Processing**
    - Generate random valid configuration events
    - Verify registry/cache updates correctly
    - Verify no exceptions thrown
    - _Requirements: 2.4, 2.5, 2.6_
  
  - [ ]* 14.4 Write property test for malformed event handling
    - **Property 4: Malformed Event Handling**
    - Generate random malformed events
    - Verify errors are logged
    - Verify processing continues without crashing
    - _Requirements: 2.7_

- [x] 15. Implement error handling
  - [x] 15.1 Create GatewayErrorResponse class
    - Implement error response structure with status, code, message, timestamp, path, correlationId
    - _Requirements: 11.1, 11.2, 11.3, 11.5_
  
  - [x] 15.2 Create GlobalErrorHandler
    - Implement WebExceptionHandler for global error handling
    - Handle authentication errors (401)
    - Handle authorization errors (403)
    - Handle rate limit errors (429 with Retry-After header)
    - Handle routing errors (404)
    - Handle internal errors (500)
    - Return JSON error responses
    - Log full error details for internal errors
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_
  
  - [ ]* 15.3 Write property test for error response format
    - **Property 22: Error Response Format**
    - Generate random gateway errors (auth, authz, rate limit, internal)
    - Verify JSON response format is correct
    - Verify status codes are correct
    - Verify Retry-After header present for rate limit errors
    - _Requirements: 11.1, 11.2, 11.3, 11.5_

- [x] 16. Implement health monitoring
  - [x] 16.1 Create custom health indicators
    - Implement RedisHealthIndicator checking Redis connectivity
    - Implement KafkaHealthIndicator checking Kafka consumer status
    - Implement ControlPlaneHealthIndicator checking control plane API
    - _Requirements: 12.2, 12.3, 12.4_
  
  - [x] 16.2 Configure Spring Boot Actuator
    - Enable health endpoint at /actuator/health
    - Enable metrics endpoint at /actuator/metrics
    - Configure health indicators to run on schedule
    - _Requirements: 12.1, 12.5_
  
  - [x] 16.3 Implement request logging
    - Create LoggingFilter with order Integer.MAX_VALUE (runs last)
    - Log timestamp, path, method, status code, duration for all requests
    - Use structured JSON logging
    - Include correlation ID in logs
    - _Requirements: 12.6_
  
  - [ ]* 16.4 Write property test for health status completeness
    - **Property 23: Health Status Completeness**
    - Call health endpoint
    - Verify Redis, Kafka, and control plane status are included
    - _Requirements: 12.2, 12.3, 12.4_
  
  - [ ]* 16.5 Write property test for request logging completeness
    - **Property 24: Request Logging Completeness**
    - Generate random requests
    - Verify logs contain timestamp, path, method, status, duration
    - _Requirements: 12.6_

- [x] 17. Checkpoint - Verify monitoring and error handling
  - Ensure all tests pass, ask the user if questions arise.

- [x] 18. Create Docker and Helm packaging
  - [x] 18.1 Create Dockerfile
    - Use eclipse-temurin:21-jre as base image
    - Copy JAR file to /app
    - Expose port 8080
    - Set ENTRYPOINT to run Spring Boot application
    - _Requirements: 14.1, 14.2_
  
  - [x] 18.2 Create Helm chart structure
    - Create Chart.yaml with metadata
    - Create values.yaml with default configuration
    - Create templates/deployment.yaml with deployment manifest
    - Create templates/service.yaml with service manifest
    - Create templates/configmap.yaml for application configuration
    - Create templates/hpa.yaml for horizontal pod autoscaling
    - _Requirements: 14.3, 14.4, 14.5, 14.6_
  
  - [x] 18.3 Create environment-specific values files
    - Create values-local.yaml for local development
    - Create values-dev.yaml for dev environment
    - Create values-prod.yaml for production
    - Configure resource limits, replicas, autoscaling per environment
    - _Requirements: 13.6, 14.6_

- [x] 19. Integration testing and documentation
  - [x] 19.1 Write integration tests
    - Test end-to-end request flow through all filters
    - Test Kafka event consumption and configuration updates
    - Test Redis cache operations
    - Test control plane bootstrap integration
    - Test health check endpoints
  
  - [x] 19.2 Create README.md
    - Document project structure
    - Document configuration properties
    - Document how to run locally
    - Document how to deploy with Helm
    - Document monitoring and troubleshooting
  
  - [x] 19.3 Create API documentation
    - Document error response formats
    - Document rate limit headers
    - Document forwarded headers
    - Document health check endpoints

- [x] 20. Final checkpoint - Complete testing and validation
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using JUnit QuickCheck
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end flows
- The gateway uses reactive programming with Spring WebFlux for high performance
- All configuration is externalized for easy deployment across environments
