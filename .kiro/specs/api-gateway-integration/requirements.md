# Requirements Document: API Gateway Integration

## Introduction

This document specifies the requirements for an API Gateway service that serves as the main ingress point for all EMF-based applications. The gateway will integrate with the EMF control plane to provide dynamic routing, authentication, authorization, JSON:API processing, and rate limiting capabilities. The gateway will be built using Spring Cloud Gateway and will subscribe to Kafka topics for real-time configuration updates from the control plane.

## Glossary

- **Gateway**: The API Gateway service that routes and processes all incoming HTTP requests
- **Control_Plane**: The EMF control plane service that manages configuration for services, collections, routes, and authorization
- **Route**: A mapping from an HTTP path pattern to a backend service endpoint
- **Collection**: A JSON:API resource type with associated fields and authorization policies
- **Route_Policy**: An authorization policy that controls access to specific HTTP methods on a route
- **Field_Policy**: An authorization policy that controls visibility of specific fields in JSON:API responses
- **JSON:API**: A specification for building APIs in JSON format with support for relationships and includes
- **Include**: A JSON:API query parameter that requests related resources to be embedded in the response
- **Rate_Limit**: A restriction on the number of requests allowed within a time window
- **Kafka_Topic**: A message queue topic used for publishing configuration change events
- **Redis**: An in-memory data store used for caching and rate limiting state
- **JWT**: JSON Web Token used for authentication
- **Principal**: The authenticated user making a request

## Requirements

### Requirement 1: Route Discovery and Management

**User Story:** As a platform operator, I want the gateway to automatically discover routes from the control plane, so that I don't have to manually configure routing rules.

#### Acceptance Criteria

1. WHEN the Gateway starts, THE Gateway SHALL fetch the complete route configuration from the Control_Plane bootstrap endpoint
2. WHEN the Gateway receives a route configuration, THE Gateway SHALL validate that all required fields are present (service ID, collection ID, path pattern, backend URL)
3. WHEN the Gateway receives valid route configuration, THE Gateway SHALL create route mappings for all active collections
4. WHEN a route configuration contains invalid data, THE Gateway SHALL log an error and skip that route
5. THE Gateway SHALL maintain an in-memory route registry indexed by path pattern

### Requirement 2: Dynamic Configuration Updates

**User Story:** As a platform operator, I want the gateway to automatically reload configurations when the control plane is updated, so that changes take effect without restarting the gateway.

#### Acceptance Criteria

1. WHEN the Gateway starts, THE Gateway SHALL subscribe to the Kafka topic for collection configuration changes
2. WHEN the Gateway starts, THE Gateway SHALL subscribe to the Kafka topic for authorization configuration changes
3. WHEN the Gateway starts, THE Gateway SHALL subscribe to the Kafka topic for service configuration changes
4. WHEN the Gateway receives a collection changed event, THE Gateway SHALL update the route registry with the new collection configuration
5. WHEN the Gateway receives an authorization changed event, THE Gateway SHALL update the authorization cache with the new policies
6. WHEN the Gateway receives a service changed event with changeType DELETED, THE Gateway SHALL remove all routes for that service
7. WHEN the Gateway receives a malformed Kafka event, THE Gateway SHALL log an error and continue processing other events

### Requirement 3: Authentication

**User Story:** As a security administrator, I want all requests to be authenticated using JWT tokens, so that only authorized users can access the system.

#### Acceptance Criteria

1. WHEN the Gateway receives a request with a valid JWT token in the Authorization header, THE Gateway SHALL extract the Principal from the token
2. WHEN the Gateway receives a request without an Authorization header, THE Gateway SHALL return HTTP 401 Unauthorized
3. WHEN the Gateway receives a request with an invalid JWT token, THE Gateway SHALL return HTTP 401 Unauthorized with an error message
4. WHEN the Gateway receives a request with an expired JWT token, THE Gateway SHALL return HTTP 401 Unauthorized
5. THE Gateway SHALL validate JWT signatures using the public key from the OIDC provider
6. THE Gateway SHALL extract user roles and claims from the JWT token for authorization decisions

### Requirement 4: Route Authorization

**User Story:** As a security administrator, I want requests to be authorized based on route policies, so that users can only access routes they have permission for.

#### Acceptance Criteria

1. WHEN the Gateway receives an authenticated request, THE Gateway SHALL look up the Route_Policy for the requested path and HTTP method
2. WHEN no Route_Policy exists for a route, THE Gateway SHALL allow the request to proceed
3. WHEN a Route_Policy exists, THE Gateway SHALL evaluate whether the Principal satisfies the policy rules
4. WHEN the Principal does not satisfy the Route_Policy, THE Gateway SHALL return HTTP 403 Forbidden
5. WHEN the Principal satisfies the Route_Policy, THE Gateway SHALL allow the request to proceed to the backend service
6. THE Gateway SHALL support policy rules based on user roles from JWT claims

### Requirement 5: Field-Level Authorization

**User Story:** As a security administrator, I want response fields to be filtered based on field policies, so that users only see data they are authorized to view.

#### Acceptance Criteria

1. WHEN the Gateway receives a JSON:API response from a backend service, THE Gateway SHALL parse the response body
2. WHEN Field_Policy entries exist for the collection, THE Gateway SHALL evaluate each policy against the Principal
3. WHEN a field has a Field_Policy that the Principal does not satisfy, THE Gateway SHALL remove that field from all resource objects in the response
4. WHEN a field has no Field_Policy, THE Gateway SHALL include the field in the response
5. WHEN the Principal satisfies all Field_Policy rules for a field, THE Gateway SHALL include the field in the response
6. THE Gateway SHALL filter fields in both the primary data array and included resources

### Requirement 6: JSON:API Include Processing

**User Story:** As an API consumer, I want to use JSON:API include parameters to fetch related resources, so that I can reduce the number of API calls.

#### Acceptance Criteria

1. WHEN the Gateway receives a request with an include query parameter, THE Gateway SHALL parse the comma-separated relationship names
2. WHEN the Gateway receives a JSON:API response with relationships, THE Gateway SHALL extract the type and id from each relationship
3. WHEN processing includes, THE Gateway SHALL check Redis cache for each related resource using the key pattern "jsonapi:{type}:{id}"
4. WHEN a related resource is found in Redis cache, THE Gateway SHALL add it to the included array in the response
5. WHEN a related resource is not found in Redis cache, THE Gateway SHALL skip that resource and log a cache miss
6. WHEN the Gateway adds included resources, THE Gateway SHALL apply Field_Policy filtering to those resources
7. WHEN the include parameter contains invalid relationship names, THE Gateway SHALL ignore those names and process valid ones

### Requirement 7: Redis Cache Integration

**User Story:** As a platform operator, I want the gateway to use Redis for caching included resources, so that JSON:API include processing is fast and efficient.

#### Acceptance Criteria

1. THE Gateway SHALL connect to Redis using the configured host and port
2. WHEN the Gateway looks up a cached resource, THE Gateway SHALL use the key pattern "jsonapi:{type}:{id}"
3. WHEN a Redis lookup fails due to connection error, THE Gateway SHALL log the error and continue without the cached resource
4. WHEN a Redis lookup succeeds, THE Gateway SHALL deserialize the JSON value into a resource object
5. THE Gateway SHALL set a connection timeout of 2 seconds for Redis operations
6. THE Gateway SHALL set a read timeout of 1 second for Redis operations

### Requirement 8: Rate Limiting

**User Story:** As a platform operator, I want to configure rate limits per route, so that I can prevent abuse and ensure fair resource usage.

#### Acceptance Criteria

1. WHEN the Gateway receives route configuration with rate limit settings, THE Gateway SHALL store the rate limit policy (requests per time window)
2. WHEN the Gateway receives a request, THE Gateway SHALL check if a rate limit policy exists for that route
3. WHEN a rate limit policy exists, THE Gateway SHALL increment the request count in Redis using the key pattern "ratelimit:{route}:{principal}"
4. WHEN the request count exceeds the rate limit, THE Gateway SHALL return HTTP 429 Too Many Requests
5. WHEN the request count is within the rate limit, THE Gateway SHALL allow the request to proceed
6. THE Gateway SHALL set TTL on rate limit keys in Redis to match the time window
7. WHEN Redis is unavailable, THE Gateway SHALL allow requests to proceed and log a warning

### Requirement 9: Request Routing

**User Story:** As an API consumer, I want my requests to be routed to the correct backend service, so that I can access the resources I need.

#### Acceptance Criteria

1. WHEN the Gateway receives a request, THE Gateway SHALL match the request path against registered route patterns
2. WHEN a matching route is found, THE Gateway SHALL forward the request to the backend service URL
3. WHEN no matching route is found, THE Gateway SHALL return HTTP 404 Not Found
4. WHEN forwarding a request, THE Gateway SHALL preserve all request headers except Authorization
5. WHEN forwarding a request, THE Gateway SHALL add an X-Forwarded-User header with the Principal username
6. WHEN forwarding a request, THE Gateway SHALL add an X-Forwarded-Roles header with the Principal roles
7. WHEN the backend service returns an error, THE Gateway SHALL return the error response to the client

### Requirement 10: Control Plane Self-Routing

**User Story:** As a platform operator, I want the control plane itself to be accessible through the gateway, so that all API traffic goes through a single entry point.

#### Acceptance Criteria

1. THE Gateway SHALL include a route for the Control_Plane service at path "/control/**"
2. WHEN the Gateway receives a request to "/control/**", THE Gateway SHALL forward it to the Control_Plane backend URL
3. WHEN forwarding to the Control_Plane, THE Gateway SHALL apply the same authentication and authorization rules as other routes
4. THE Gateway SHALL allow unauthenticated access to the Control_Plane bootstrap endpoint "/control/bootstrap"

### Requirement 11: Error Handling

**User Story:** As an API consumer, I want clear error messages when requests fail, so that I can understand what went wrong.

#### Acceptance Criteria

1. WHEN the Gateway encounters an authentication error, THE Gateway SHALL return a JSON error response with status code and message
2. WHEN the Gateway encounters an authorization error, THE Gateway SHALL return a JSON error response with status code and message
3. WHEN the Gateway encounters a rate limit error, THE Gateway SHALL return a JSON error response with status code, message, and retry-after header
4. WHEN the Gateway encounters a backend service error, THE Gateway SHALL return the backend error response unchanged
5. WHEN the Gateway encounters an internal error, THE Gateway SHALL return HTTP 500 with a generic error message and log the full error details

### Requirement 12: Health and Monitoring

**User Story:** As a platform operator, I want to monitor the gateway's health and performance, so that I can detect and resolve issues quickly.

#### Acceptance Criteria

1. THE Gateway SHALL expose a health check endpoint at "/actuator/health"
2. WHEN the health check endpoint is called, THE Gateway SHALL return the status of Redis connection
3. WHEN the health check endpoint is called, THE Gateway SHALL return the status of Kafka consumer connection
4. WHEN the health check endpoint is called, THE Gateway SHALL return the status of Control_Plane connectivity
5. THE Gateway SHALL expose metrics endpoint at "/actuator/metrics" with request counts, latencies, and error rates
6. THE Gateway SHALL log all requests with timestamp, path, method, status code, and duration

### Requirement 13: Configuration Management

**User Story:** As a platform operator, I want to configure the gateway using standard Spring Boot configuration files, so that I can easily deploy it in different environments.

#### Acceptance Criteria

1. THE Gateway SHALL read Control_Plane URL from configuration property "emf.gateway.control-plane.url"
2. THE Gateway SHALL read Kafka bootstrap servers from configuration property "spring.kafka.bootstrap-servers"
3. THE Gateway SHALL read Redis host and port from configuration properties "spring.data.redis.host" and "spring.data.redis.port"
4. THE Gateway SHALL read JWT issuer URI from configuration property "spring.security.oauth2.resourceserver.jwt.issuer-uri"
5. THE Gateway SHALL read rate limit defaults from configuration property "emf.gateway.rate-limit.default"
6. THE Gateway SHALL support environment-specific configuration profiles (local, dev, staging, prod)

### Requirement 14: Deployment Packaging

**User Story:** As a platform operator, I want the gateway packaged as a Docker image with Helm charts, so that I can deploy it to Kubernetes.

#### Acceptance Criteria

1. THE Gateway SHALL be packaged as a Docker image with base image eclipse-temurin:21-jre
2. THE Gateway SHALL include a Dockerfile that builds the Spring Boot application
3. THE Gateway SHALL include a Helm chart with configurable values for all configuration properties
4. THE Gateway SHALL include Kubernetes deployment manifests with resource limits and health checks
5. THE Gateway SHALL include Kubernetes service manifest exposing port 8080
6. THE Gateway SHALL support horizontal pod autoscaling based on CPU and memory usage
