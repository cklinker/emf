# Integration Test Environment Architecture

This document describes the architecture of the EMF integration test environment, including component interactions, data flows, and design decisions.

## Table of Contents

- [Overview](#overview)
- [Architecture Layers](#architecture-layers)
- [Component Diagram](#component-diagram)
- [Service Interactions](#service-interactions)
- [Data Flow](#data-flow)
- [Test Execution Flow](#test-execution-flow)
- [Design Decisions](#design-decisions)
- [Technology Stack](#technology-stack)

## Overview

The integration test environment is a complete, self-contained EMF platform deployment running in Docker containers. It validates the integration between all platform components through automated tests that exercise real request flows.

### Key Characteristics

- **Isolated**: Each test run uses fresh containers and data
- **Reproducible**: Deterministic setup and teardown
- **Comprehensive**: Tests all integration points
- **Fast**: Complete suite runs in < 5 minutes
- **Realistic**: Uses actual services, not mocks

## Architecture Layers

The test environment is organized into four layers:

```mermaid
graph TB
    subgraph "Test Execution Layer"
        TF[Test Framework]
        TR[Test Runner]
        TH[Test Helpers]
        TF --> TR
        TR --> TH
    end
    
    subgraph "Platform Services Layer"
        GW[API Gateway<br/>:8080]
        CP[Control Plane<br/>:8081]
        GW --> CP
    end
    
    subgraph "Sample Service Layer"
        SS[Sample Service<br/>:8082]
    end
    
    subgraph "Infrastructure Layer"
        PG[(PostgreSQL<br/>:5432)]
        RD[(Redis<br/>:6379)]
        KF[Kafka<br/>:9092]
        KC[Keycloak<br/>:8180]
    end
    
    TR --> GW
    GW --> SS
    CP --> PG
    CP --> KF
    SS --> PG
    SS --> RD
    GW --> RD
    GW --> KF
    GW --> KC
    
    style TF fill:#e1f5ff
    style TR fill:#e1f5ff
    style TH fill:#e1f5ff
    style GW fill:#fff4e6
    style CP fill:#fff4e6
    style SS fill:#f3e5f5
    style PG fill:#e8f5e9
    style RD fill:#e8f5e9
    style KF fill:#e8f5e9
    style KC fill:#e8f5e9
```

### Layer 1: Infrastructure Layer

Provides foundational services:

- **PostgreSQL**: Persistent storage for collections and configuration
- **Redis**: Caching and rate limiting
- **Kafka**: Event distribution for configuration updates
- **Keycloak**: OIDC authentication and JWT token issuance

### Layer 2: Platform Services Layer

Core EMF platform services:

- **Control Plane**: Manages collections, authorization policies, and service registry
- **API Gateway**: Routes requests, enforces authentication/authorization, processes JSON:API features

### Layer 3: Sample Service Layer

Test domain service:

- **Sample Service**: Implements projects and tasks collections using emf-platform/runtime-core

### Layer 4: Test Execution Layer

Test orchestration and utilities:

- **Test Framework**: JUnit 5 + jqwik for property-based testing
- **Test Runner**: Orchestrates environment startup and test execution
- **Test Helpers**: Reusable utilities for authentication, test data, and assertions

## Component Diagram

```mermaid
graph LR
    subgraph "Test Framework"
        ITB[IntegrationTestBase]
        AH[AuthenticationHelper]
        TDH[TestDataHelper]
        ITB --> AH
        ITB --> TDH
    end
    
    subgraph "API Gateway"
        AR[Auth Filter]
        AZ[Authz Filter]
        RR[Route Registry]
        FF[Field Filter]
        IP[Include Processor]
        AR --> AZ
        AZ --> RR
        RR --> FF
        FF --> IP
    end
    
    subgraph "Control Plane"
        CR[Collection Registry]
        PR[Policy Registry]
        SR[Service Registry]
        EP[Event Publisher]
        CR --> EP
        PR --> EP
        SR --> EP
    end
    
    subgraph "Sample Service"
        CI[CollectionInitializer]
        DCR[DynamicCollectionRouter]
        RCS[ResourceCacheService]
        CEL[CacheEventListener]
        CI --> DCR
        DCR --> RCS
        DCR --> CEL
    end
    
    ITB --> AR
    RR --> DCR
    EP --> RR
    RCS --> Redis[(Redis)]
    CR --> PG[(PostgreSQL)]
    DCR --> PG
    AR --> KC[Keycloak]
    
    style ITB fill:#e1f5ff
    style AH fill:#e1f5ff
    style TDH fill:#e1f5ff
    style AR fill:#fff4e6
    style AZ fill:#fff4e6
    style RR fill:#fff4e6
    style FF fill:#fff4e6
    style IP fill:#fff4e6
    style CR fill:#fff4e6
    style PR fill:#fff4e6
    style SR fill:#fff4e6
    style EP fill:#fff4e6
    style CI fill:#f3e5f5
    style DCR fill:#f3e5f5
    style RCS fill:#f3e5f5
    style CEL fill:#f3e5f5
```

## Service Interactions

### 1. Service Startup Sequence

```mermaid
sequenceDiagram
    participant DC as Docker Compose
    participant PG as PostgreSQL
    participant RD as Redis
    participant KF as Kafka
    participant KC as Keycloak
    participant CP as Control Plane
    participant GW as Gateway
    participant SS as Sample Service
    
    DC->>PG: Start
    DC->>RD: Start
    DC->>KF: Start
    DC->>KC: Start
    
    PG-->>DC: Health OK
    RD-->>DC: Health OK
    KF-->>DC: Health OK
    KC-->>DC: Health OK
    
    DC->>CP: Start
    CP->>PG: Connect
    CP->>KF: Connect
    CP-->>DC: Health OK
    
    DC->>GW: Start
    GW->>CP: Bootstrap config
    CP-->>GW: Collections, policies, services
    GW->>KF: Subscribe to events
    GW->>RD: Connect
    GW->>KC: Validate OIDC config
    GW-->>DC: Health OK
    
    DC->>SS: Start
    SS->>PG: Initialize collections
    SS->>RD: Connect
    SS->>CP: Register service
    SS->>CP: Register collections
    SS-->>DC: Health OK
```

### 2. Test Request Flow

```mermaid
sequenceDiagram
    participant T as Test
    participant KC as Keycloak
    participant GW as Gateway
    participant SS as Sample Service
    participant PG as PostgreSQL
    participant RD as Redis
    
    T->>KC: POST /token (username, password)
    KC-->>T: JWT token
    
    T->>GW: POST /api/collections/projects<br/>(Authorization: Bearer token)
    GW->>KC: Validate JWT
    KC-->>GW: Token valid, user claims
    GW->>GW: Check authorization policy
    GW->>SS: POST /api/collections/projects
    SS->>SS: Validate request
    SS->>PG: INSERT INTO projects
    PG-->>SS: Project created
    SS->>RD: Cache resource
    SS-->>GW: 201 Created (JSON:API)
    GW->>GW: Apply field filtering
    GW-->>T: 201 Created (JSON:API)
    
    T->>GW: GET /api/collections/projects/{id}?include=tasks
    GW->>KC: Validate JWT
    GW->>SS: GET /api/collections/projects/{id}
    SS->>PG: SELECT FROM projects
    SS->>RD: GET related tasks from cache
    SS-->>GW: 200 OK (with included)
    GW->>GW: Apply field filtering
    GW-->>T: 200 OK (JSON:API)
```

### 3. Configuration Update Flow

```mermaid
sequenceDiagram
    participant T as Test
    participant CP as Control Plane
    participant KF as Kafka
    participant GW as Gateway
    
    T->>CP: POST /control/collections<br/>(new collection)
    CP->>CP: Validate collection
    CP->>PG: INSERT INTO collections
    CP->>KF: Publish CollectionChanged event
    KF-->>GW: CollectionChanged event
    GW->>GW: Update route registry
    GW->>GW: Create new route
    CP-->>T: 201 Created
    
    Note over T,GW: New route is now available
    
    T->>GW: GET /api/collections/new-collection
    GW->>GW: Route found in registry
    GW->>Backend: Forward request
```

## Data Flow

### 1. CRUD Operation Data Flow

```mermaid
graph LR
    A[Test] -->|1. POST with JWT| B[Gateway]
    B -->|2. Validate token| C[Keycloak]
    C -->|3. Token valid| B
    B -->|4. Check policy| D[Policy Cache]
    D -->|5. Authorized| B
    B -->|6. Route request| E[Sample Service]
    E -->|7. Validate data| F[ValidationEngine]
    F -->|8. Valid| E
    E -->|9. INSERT| G[(PostgreSQL)]
    G -->|10. Row inserted| E
    E -->|11. Cache resource| H[(Redis)]
    E -->|12. JSON:API response| B
    B -->|13. Filter fields| I[Field Filter]
    I -->|14. Filtered response| A
    
    style A fill:#e1f5ff
    style B fill:#fff4e6
    style C fill:#e8f5e9
    style D fill:#fff4e6
    style E fill:#f3e5f5
    style F fill:#f3e5f5
    style G fill:#e8f5e9
    style H fill:#e8f5e9
    style I fill:#fff4e6
```

### 2. Include Parameter Data Flow

```mermaid
graph LR
    A[Test] -->|1. GET ?include=tasks| B[Gateway]
    B -->|2. Route request| C[Sample Service]
    C -->|3. SELECT primary| D[(PostgreSQL)]
    D -->|4. Project data| C
    C -->|5. Parse include param| E[Include Processor]
    E -->|6. GET related IDs| F[(Redis)]
    F -->|7. Cached tasks| E
    E -->|8. Build included array| C
    C -->|9. JSON:API with included| B
    B -->|10. Filter fields| G[Field Filter]
    G -->|11. Filtered response| A
    
    style A fill:#e1f5ff
    style B fill:#fff4e6
    style C fill:#f3e5f5
    style D fill:#e8f5e9
    style E fill:#f3e5f5
    style F fill:#e8f5e9
    style G fill:#fff4e6
```

### 3. Event-Driven Configuration Data Flow

```mermaid
graph TB
    A[Control Plane] -->|1. Configuration change| B[(PostgreSQL)]
    A -->|2. Publish event| C[Kafka Topic]
    C -->|3. Consume event| D[Gateway Consumer]
    D -->|4. Update cache| E[Config Cache]
    E -->|5. New config active| F[Request Handler]
    
    style A fill:#fff4e6
    style B fill:#e8f5e9
    style C fill:#e8f5e9
    style D fill:#fff4e6
    style E fill:#fff4e6
    style F fill:#fff4e6
```

## Test Execution Flow

### 1. Test Suite Execution

```mermaid
graph TB
    Start[Start Test Suite] --> Build[Build Docker Images]
    Build --> StartInfra[Start Infrastructure<br/>PostgreSQL, Redis, Kafka, Keycloak]
    StartInfra --> WaitInfra{Infrastructure<br/>Healthy?}
    WaitInfra -->|No| WaitInfra
    WaitInfra -->|Yes| StartPlatform[Start Platform Services<br/>Control Plane, Gateway]
    StartPlatform --> WaitPlatform{Platform<br/>Healthy?}
    WaitPlatform -->|No| WaitPlatform
    WaitPlatform -->|Yes| StartSample[Start Sample Service]
    StartSample --> WaitSample{Sample Service<br/>Healthy?}
    WaitSample -->|No| WaitSample
    WaitSample -->|Yes| RunTests[Run Integration Tests]
    RunTests --> Cleanup[Cleanup Resources]
    Cleanup --> Stop[Stop Containers]
    Stop --> End[Generate Reports]
    
    style Start fill:#e1f5ff
    style Build fill:#e1f5ff
    style RunTests fill:#e1f5ff
    style End fill:#e1f5ff
```

### 2. Individual Test Execution

```mermaid
graph TB
    Start[Test Method Start] --> Setup[Test Setup]
    Setup --> Auth[Get JWT Token]
    Auth --> CreateData[Create Test Data]
    CreateData --> Execute[Execute Test Logic]
    Execute --> Assert[Assert Results]
    Assert --> Cleanup[Cleanup Test Data]
    Cleanup --> End[Test Complete]
    
    style Start fill:#e1f5ff
    style Execute fill:#fff4e6
    style Assert fill:#e1f5ff
    style End fill:#e1f5ff
```

### 3. Property-Based Test Execution

```mermaid
graph TB
    Start[Property Test Start] --> Generate[Generate Random Input]
    Generate --> Execute[Execute Test with Input]
    Execute --> Check{Property<br/>Holds?}
    Check -->|Yes| Count{Iterations<br/>< 100?}
    Count -->|Yes| Generate
    Count -->|No| Pass[Test Passed]
    Check -->|No| Shrink[Shrink to Minimal<br/>Failing Example]
    Shrink --> Fail[Test Failed]
    Pass --> End[Report Results]
    Fail --> End
    
    style Start fill:#e1f5ff
    style Generate fill:#fff4e6
    style Execute fill:#fff4e6
    style Pass fill:#c8e6c9
    style Fail fill:#ffcdd2
    style End fill:#e1f5ff
```

## Design Decisions

### 1. Use of EMF Runtime-Core Library

**Decision**: Sample service uses emf-platform/runtime-core instead of custom JPA entities

**Rationale**:
- Demonstrates real-world usage of the platform
- Automatic table creation via `StorageAdapter`
- Automatic REST API via `DynamicCollectionRouter`
- Built-in validation, pagination, sorting, filtering
- Reduces boilerplate code in sample service

**Trade-offs**:
- Adds dependency on runtime-core library
- Requires understanding of collection definition API
- Less flexibility for custom business logic

### 2. Redis for Include Processing

**Decision**: Cache resources in Redis for JSON:API include parameter processing

**Rationale**:
- Fast retrieval of related resources
- Reduces database queries
- Demonstrates caching patterns
- Tests cache invalidation logic

**Trade-offs**:
- Adds Redis dependency
- Requires cache consistency management
- Cache misses must be handled gracefully

### 3. Kafka for Configuration Updates

**Decision**: Use Kafka events for dynamic configuration updates

**Rationale**:
- Enables zero-downtime configuration changes
- Decouples control plane from gateway
- Demonstrates event-driven architecture
- Tests eventual consistency

**Trade-offs**:
- Adds Kafka dependency
- Requires event schema management
- Eventual consistency complexity

### 4. Dual Testing Approach

**Decision**: Use both unit tests and property-based tests

**Rationale**:
- Unit tests validate specific scenarios
- Property tests validate universal properties
- Comprehensive coverage with different approaches
- Demonstrates testing best practices

**Trade-offs**:
- More tests to maintain
- Longer test execution time
- Requires understanding of property-based testing

### 5. Docker Compose for Orchestration

**Decision**: Use Docker Compose instead of Testcontainers

**Rationale**:
- Simpler setup for developers
- Easier to debug (can inspect running containers)
- Reusable for local development
- Matches production deployment patterns

**Trade-offs**:
- Less programmatic control
- Requires Docker Compose installation
- Harder to parallelize tests

### 6. Keycloak for Authentication

**Decision**: Use Keycloak instead of mock authentication

**Rationale**:
- Tests real OIDC flows
- Validates JWT token handling
- Demonstrates production authentication
- Tests token expiration and refresh

**Trade-offs**:
- Slower startup time
- More complex configuration
- Additional resource usage

## Technology Stack

### Infrastructure

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Database | PostgreSQL | 15 | Persistent storage |
| Cache | Redis | 7 | Caching and rate limiting |
| Message Broker | Kafka | 3.6 (KRaft) | Event distribution |
| Identity Provider | Keycloak | 23 | OIDC authentication |

### Platform Services

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Control Plane | Spring Boot | 3.2 | Configuration management |
| API Gateway | Spring Cloud Gateway | 4.1 | Request routing |
| Sample Service | Spring Boot | 3.2 | Test domain service |

### Testing Framework

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Test Framework | JUnit 5 | 5.10 | Test execution |
| Property Testing | jqwik | 1.8 | Property-based tests |
| HTTP Client | RestTemplate | (Spring) | API requests |
| Assertions | AssertJ | 3.24 | Fluent assertions |
| Async Testing | Awaitility | 4.2 | Waiting for conditions |

### Build and Deployment

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Build Tool | Maven | 3.9 | Dependency management |
| Container Runtime | Docker | 20.10+ | Container execution |
| Orchestration | Docker Compose | 2.0+ | Multi-container apps |
| Java Runtime | Eclipse Temurin | 21 | JVM |

## Network Architecture

```mermaid
graph TB
    subgraph "Docker Network: emf-network"
        subgraph "Test Host"
            T[Test Runner<br/>localhost]
        end
        
        subgraph "Gateway Tier"
            GW[gateway:8080]
        end
        
        subgraph "Service Tier"
            CP[control-plane:8080]
            SS[sample-service:8080]
        end
        
        subgraph "Data Tier"
            PG[postgres:5432]
            RD[redis:6379]
            KF[kafka:9092]
            KC[keycloak:8080]
        end
    end
    
    T -->|localhost:8080| GW
    T -->|localhost:8081| CP
    T -->|localhost:8082| SS
    
    GW --> CP
    GW --> SS
    GW --> RD
    GW --> KF
    GW --> KC
    
    CP --> PG
    CP --> KF
    
    SS --> PG
    SS --> RD
    
    style T fill:#e1f5ff
    style GW fill:#fff4e6
    style CP fill:#fff4e6
    style SS fill:#f3e5f5
    style PG fill:#e8f5e9
    style RD fill:#e8f5e9
    style KF fill:#e8f5e9
    style KC fill:#e8f5e9
```

### Port Mapping

| Service | Internal Port | External Port | Purpose |
|---------|--------------|---------------|---------|
| API Gateway | 8080 | 8080 | Main API endpoint |
| Control Plane | 8080 | 8081 | Management API |
| Sample Service | 8080 | 8082 | Test service API |
| PostgreSQL | 5432 | 5432 | Database access |
| Redis | 6379 | 6379 | Cache access |
| Kafka | 9092 | 9092 | Event streaming |
| Keycloak | 8080 | 8180 | Authentication |

## Security Architecture

### Authentication Flow

```mermaid
sequenceDiagram
    participant T as Test
    participant KC as Keycloak
    participant GW as Gateway
    participant SS as Sample Service
    
    T->>KC: POST /token<br/>(client_id, username, password)
    KC->>KC: Validate credentials
    KC->>KC: Generate JWT
    KC-->>T: JWT token
    
    T->>GW: Request with<br/>Authorization: Bearer {token}
    GW->>GW: Extract JWT
    GW->>KC: Validate JWT signature
    KC-->>GW: Signature valid
    GW->>GW: Extract claims<br/>(user, roles)
    GW->>GW: Check authorization
    GW->>SS: Forward request
    SS-->>GW: Response
    GW-->>T: Response
```

### Authorization Model

```mermaid
graph TB
    User[User] -->|has| Roles[Roles]
    Roles -->|satisfy| Policies[Authorization Policies]
    Policies -->|control| Routes[Route Access]
    Policies -->|control| Fields[Field Visibility]
    
    Routes -->|allow/deny| Endpoints[API Endpoints]
    Fields -->|filter| Responses[API Responses]
    
    style User fill:#e1f5ff
    style Roles fill:#fff4e6
    style Policies fill:#fff4e6
    style Routes fill:#f3e5f5
    style Fields fill:#f3e5f5
```

## Scalability Considerations

### Current Limitations

- Single instance of each service
- No load balancing
- No horizontal scaling
- Limited to local development

### Production Differences

| Aspect | Test Environment | Production |
|--------|-----------------|------------|
| Instances | Single | Multiple replicas |
| Load Balancing | None | Kubernetes Service |
| Database | Single PostgreSQL | Clustered database |
| Cache | Single Redis | Redis Cluster |
| Message Broker | Single Kafka | Kafka Cluster |
| Persistence | Docker volumes | Persistent volumes |
| Networking | Docker network | Kubernetes network |

## Monitoring and Observability

### Health Checks

All services expose Spring Boot Actuator health endpoints:

```
GET /actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "kafka": {"status": "UP"}
  }
}
```

### Logging

Services log to stdout/stderr, collected by Docker:

```bash
# View logs
docker-compose logs <service-name>

# Follow logs
docker-compose logs -f <service-name>

# View all logs
docker-compose logs
```

### Metrics

Spring Boot Actuator provides metrics:

```
GET /actuator/metrics
GET /actuator/metrics/{metric-name}
```

## Future Enhancements

### Planned Improvements

1. **Testcontainers Integration**: Programmatic container management
2. **Parallel Test Execution**: Run tests concurrently
3. **Performance Testing**: Load and stress tests
4. **Chaos Engineering**: Failure injection tests
5. **Contract Testing**: API contract validation
6. **Mutation Testing**: Test quality validation
7. **Visual Regression**: UI component testing
8. **Distributed Tracing**: Request flow visualization

### Potential Optimizations

1. **Container Caching**: Reuse containers between test runs
2. **Incremental Testing**: Run only affected tests
3. **Test Sharding**: Distribute tests across runners
4. **Resource Pooling**: Share infrastructure services
5. **Snapshot Testing**: Faster database initialization

## References

- [EMF Platform Documentation](emf-docs/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [JUnit 5 Documentation](https://junit.org/junit5/)
- [jqwik Documentation](https://jqwik.net/)
- [JSON:API Specification](https://jsonapi.org/)
