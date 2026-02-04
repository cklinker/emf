# EMF Integration Tests

Comprehensive integration testing suite for the EMF (Enterprise Microservice Framework) platform. This suite provides automated, reproducible testing of the complete platform stack including infrastructure services, core platform services, and sample domain services.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Test Environment](#test-environment)
- [Running Tests](#running-tests)
- [Test Categories](#test-categories)
- [Adding New Tests](#adding-new-tests)
- [Troubleshooting](#troubleshooting)
- [CI/CD Integration](#cicd-integration)

## Overview

The integration test suite validates end-to-end functionality across the EMF platform:

- **Infrastructure Layer**: PostgreSQL, Redis, Kafka, Keycloak
- **Platform Services**: Control Plane, API Gateway
- **Sample Service**: Test domain service with JSON:API collections
- **Test Framework**: Orchestration and test utilities

The suite uses a dual testing approach:
- **Unit Tests**: Specific scenarios and edge cases
- **Property-Based Tests**: Universal properties across randomized inputs

## Prerequisites

### Required Software

- **Docker**: 20.10 or higher
- **Docker Compose**: 2.0 or higher
- **Java**: 21 or higher (for running tests)
- **Maven**: 3.8 or higher (for building services)

### System Requirements

- **CPU**: 4+ cores recommended
- **Memory**: 8GB+ RAM recommended
- **Disk**: 10GB+ free space

### Verify Installation

```bash
# Check Docker
docker --version
docker-compose --version

# Check Java
java -version

# Check Maven
mvn -version
```

## Quick Start

### 1. Clone and Build

```bash
# Navigate to workspace root
cd /path/to/workspace

# Build all services (optional - Docker will build if needed)
docker-compose build
```

### 2. Start Environment

```bash
# Start all services
docker-compose up -d

# Wait for services to be healthy (60-90 seconds)
./scripts/wait-for-services.sh
```

### 3. Run Tests

```bash
# Run all integration tests
./scripts/run-integration-tests.sh

# Run specific test category
./scripts/run-integration-tests.sh --category=authentication

# Run in watch mode for development
./scripts/run-integration-tests.sh --watch
```

### 4. Cleanup

```bash
# Stop all services
docker-compose down

# Remove volumes (clean slate)
docker-compose down -v
```

## Test Environment

### Services

The Docker Compose environment includes:

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL | 5432 | Database for control plane and sample service |
| Redis | 6379 | Caching and rate limiting |
| Kafka | 9092 | Configuration event distribution |
| Keycloak | 8180 | OIDC authentication |
| Control Plane | 8081 | Collection and authorization management |
| API Gateway | 8080 | Request routing and policy enforcement |
| Sample Service | 8082 | Test domain service |

### Test Users

Pre-configured in Keycloak:

| Username | Password | Roles | Purpose |
|----------|----------|-------|---------|
| admin | admin | ADMIN, USER | Full access testing |
| user | user | USER | Standard user testing |
| guest | guest | (none) | Unauthorized access testing |

### Test Collections

Sample service provides two collections:

**Projects Collection**:
- Fields: name (required), description, status, created_at
- Endpoint: `/api/collections/projects`

**Tasks Collection**:
- Fields: title (required), description, completed, project_id
- Relationship: belongs to project
- Endpoint: `/api/collections/tasks`

## Running Tests

### All Tests

```bash
# Run complete test suite
./scripts/run-integration-tests.sh
```

Expected output:
```
Starting Docker environment...
Waiting for services to be healthy...
✓ PostgreSQL is healthy
✓ Redis is healthy
✓ Kafka is healthy
✓ Keycloak is healthy
✓ Control Plane is healthy
✓ API Gateway is healthy
✓ Sample Service is healthy

Running integration tests...
[INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Specific Test Categories

```bash
# Authentication tests only
./scripts/run-integration-tests.sh --category=authentication

# Authorization tests only
./scripts/run-integration-tests.sh --category=authorization

# CRUD operation tests
./scripts/run-integration-tests.sh --category=crud

# All available categories
./scripts/run-integration-tests.sh --list-categories
```

### Development Mode

```bash
# Watch mode - re-run tests on file changes
./scripts/run-integration-tests.sh --watch

# Verbose logging
./scripts/run-integration-tests.sh --verbose

# Keep environment running after tests
./scripts/run-integration-tests.sh --no-cleanup
```

### CI Mode

```bash
# CI/CD optimized execution
./scripts/run-integration-tests.sh --ci

# Generates:
# - JUnit XML reports in target/surefire-reports/
# - HTML reports in target/site/
# - Collects logs on failure
```

## Test Categories

### Infrastructure Tests
- Docker environment setup
- Service health checks
- Network connectivity
- Database schema validation

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `HealthCheckIntegrationTest.java`
- `ControlPlaneBootstrapIntegrationTest.java`

### Authentication Tests
- JWT token validation
- Token acquisition from Keycloak
- Invalid/expired token handling
- User identity extraction

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `AuthenticationIntegrationTest.java`

### Authorization Tests
- Route policy enforcement
- Field-level filtering
- Role-based access control
- Dynamic policy updates

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `AuthorizationIntegrationTest.java`

### CRUD Operation Tests
- Create, read, update, delete operations
- JSON:API format compliance
- Validation and error handling
- Resource ID uniqueness

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `CollectionCrudIntegrationTest.java`

### Relationship Tests
- Creating resources with relationships
- Relationship persistence
- Referential integrity
- Relationship queries

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `RelatedCollectionsIntegrationTest.java`

### Include Parameter Tests
- JSON:API include processing
- Related resource embedding
- Cache-based resource fetching
- Invalid include handling

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `IncludeParameterIntegrationTest.java`

### Cache Integration Tests
- Redis caching behavior
- Cache key patterns
- TTL configuration
- Cache invalidation

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `CacheIntegrationTest.java`
- `RedisCacheIntegrationTest.java`

### Event-Driven Configuration Tests
- Kafka event publishing
- Configuration update propagation
- Dynamic reconfiguration
- Event processing

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `ConfigurationUpdateIntegrationTest.java`
- `KafkaConfigurationUpdateIntegrationTest.java`

### Collection Management Tests
- Collection creation via control plane
- Collection registration
- Route creation
- Schema validation

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `CollectionManagementIntegrationTest.java`

### Error Handling Tests
- Validation errors
- Authentication/authorization errors
- Backend service errors
- Infrastructure failure resilience

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `ErrorHandlingIntegrationTest.java`

### End-to-End Tests
- Complete request flows
- Multi-step scenarios
- Cross-component integration
- Real-world use cases

**Location**: `emf-gateway/src/test/java/com/emf/gateway/integration/`
- `EndToEndFlowIntegrationTest.java`
- `EndToEndRequestFlowIntegrationTest.java`

## Adding New Tests

### 1. Create Test Class

Create a new test class extending `IntegrationTestBase`:

```java
package com.emf.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MyNewIntegrationTest extends IntegrationTestBase {
    
    @Autowired
    private AuthenticationHelper authHelper;
    
    @Autowired
    private TestDataHelper testDataHelper;
    
    @Test
    void testMyNewFeature() {
        // Arrange
        String token = authHelper.getAdminToken();
        String projectId = testDataHelper.createProject(
            "Test Project", 
            "Description", 
            "ACTIVE"
        );
        
        // Act
        ResponseEntity<Map> response = restTemplate.exchange(
            GATEWAY_URL + "/api/collections/projects/" + projectId,
            HttpMethod.GET,
            new HttpEntity<>(authHelper.createAuthHeaders(token)),
            Map.class
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
    
    @Override
    protected void cleanupTestData() {
        // Clean up any test resources
        testDataHelper.deleteAllTestProjects();
    }
}
```

### 2. Add Property-Based Test (Optional)

For universal properties, use jqwik:

```java
import net.jqwik.api.*;

@Property(tries = 100)
@Tag("Feature: local-integration-testing")
@Tag("Property 29: My New Property")
void testMyProperty(@ForAll("validProjects") Project project) {
    // Test that property holds for all valid projects
    String token = authHelper.getAdminToken();
    String projectId = testDataHelper.createProject(
        project.getName(),
        project.getDescription(),
        project.getStatus()
    );
    
    // Verify property
    // ...
}

@Provide
Arbitrary<Project> validProjects() {
    return Combinators.combine(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
        Arbitraries.strings().ofMaxLength(500),
        Arbitraries.of("PLANNING", "ACTIVE", "COMPLETED", "ARCHIVED")
    ).as((name, description, status) -> 
        new Project(name, description, status)
    );
}
```

### 3. Add Test to Suite

Tests are automatically discovered by JUnit. To add to a specific category:

```java
@Tag("authentication")  // For category filtering
@Tag("Feature: local-integration-testing")  // For feature tracking
public class MyNewIntegrationTest extends IntegrationTestBase {
    // ...
}
```

### 4. Run Your Test

```bash
# Run specific test class
mvn test -Dtest=MyNewIntegrationTest

# Run via script with category
./scripts/run-integration-tests.sh --category=authentication
```

### Best Practices

1. **Extend IntegrationTestBase**: Always extend the base class for common setup
2. **Use Test Helpers**: Leverage `AuthenticationHelper` and `TestDataHelper`
3. **Clean Up Resources**: Implement `cleanupTestData()` to remove test data
4. **Use Unique Identifiers**: Generate unique names/IDs to avoid conflicts
5. **Test Isolation**: Don't depend on other tests' state
6. **Meaningful Assertions**: Use descriptive assertion messages
7. **Tag Appropriately**: Add tags for categorization and filtering

## Troubleshooting

### Services Not Starting

**Problem**: Services fail health checks or don't start

**Solutions**:
```bash
# Check service logs
docker-compose logs <service-name>

# Check all service status
docker-compose ps

# Restart specific service
docker-compose restart <service-name>

# Rebuild and restart
docker-compose up -d --build <service-name>

# Check resource usage
docker stats
```

### Tests Failing

**Problem**: Tests fail unexpectedly

**Solutions**:
```bash
# Run with verbose logging
./scripts/run-integration-tests.sh --verbose

# Check service health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health

# Check Keycloak realm
./scripts/test-keycloak-realm.sh

# Verify sample service
./scripts/verify-sample-service.sh

# Run specific failing test
mvn test -Dtest=FailingTestClass#failingMethod
```

### Port Conflicts

**Problem**: Ports already in use

**Solutions**:
```bash
# Check what's using ports
lsof -i :8080
lsof -i :8081
lsof -i :8082

# Stop conflicting services
docker-compose down

# Or modify ports in docker-compose.yml
```

### Performance Issues

**Problem**: Tests run slowly or timeout

**Solutions**:
```bash
# Increase Docker resources (Docker Desktop)
# Settings → Resources → Increase CPU/Memory

# Clean up Docker
docker system prune -a
docker volume prune

# Check disk space
df -h

# Reduce test iterations (property tests)
# Edit test: @Property(tries = 10)  // Reduced from 100
```

### Database Issues

**Problem**: Database connection errors or schema issues

**Solutions**:
```bash
# Reset database
docker-compose down -v
docker-compose up -d postgres

# Check database logs
docker-compose logs postgres

# Connect to database
docker exec -it postgres psql -U emf -d emf_control_plane

# Verify tables exist
\dt

# Check sample service initialization
docker-compose logs sample-service | grep "Collection initialized"
```

### Cache Issues

**Problem**: Redis connection errors or cache inconsistencies

**Solutions**:
```bash
# Check Redis
docker-compose logs redis

# Connect to Redis
docker exec -it redis redis-cli

# Check cached keys
KEYS jsonapi:*

# Clear cache
FLUSHALL

# Restart Redis
docker-compose restart redis
```

### Kafka Issues

**Problem**: Event processing failures

**Solutions**:
```bash
# Check Kafka logs
docker-compose logs kafka

# List topics
docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Check consumer groups
docker exec -it kafka kafka-consumer-groups.sh --list --bootstrap-server localhost:9092

# Restart Kafka
docker-compose restart kafka
```

### Common Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Connection refused" | Service not started | Wait for health checks, check logs |
| "401 Unauthorized" | Invalid/missing token | Check Keycloak, verify token acquisition |
| "404 Not Found" | Route not registered | Verify service registration, check control plane |
| "500 Internal Server Error" | Backend failure | Check service logs, verify database connection |
| "Timeout waiting for services" | Slow startup | Increase timeout, check resource usage |

## CI/CD Integration

### GitHub Actions

Example workflow:

```yaml
name: Integration Tests

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Cache Docker layers
        uses: actions/cache@v3
        with:
          path: /tmp/.buildx-cache
          key: ${{ runner.os }}-buildx-${{ github.sha }}
      
      - name: Start Docker environment
        run: docker-compose up -d
      
      - name: Wait for services
        run: ./scripts/wait-for-services.sh
      
      - name: Run integration tests
        run: ./scripts/run-integration-tests.sh --ci
      
      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: |
            target/surefire-reports/
            target/site/
      
      - name: Upload logs
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: service-logs
          path: docker-logs/
      
      - name: Cleanup
        if: always()
        run: docker-compose down -v
```

### Jenkins

Example Jenkinsfile:

```groovy
pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh 'docker-compose build'
            }
        }
        
        stage('Start Environment') {
            steps {
                sh 'docker-compose up -d'
                sh './scripts/wait-for-services.sh'
            }
        }
        
        stage('Run Tests') {
            steps {
                sh './scripts/run-integration-tests.sh --ci'
            }
        }
    }
    
    post {
        always {
            junit 'target/surefire-reports/*.xml'
            sh 'docker-compose down -v'
        }
        failure {
            sh 'docker-compose logs > jenkins-logs.txt'
            archiveArtifacts 'jenkins-logs.txt'
        }
    }
}
```

## Performance Targets

- **Full test suite**: < 5 minutes
- **Individual test**: < 10 seconds
- **Service startup**: < 30 seconds
- **Health check convergence**: < 60 seconds

## Additional Resources

- [Architecture Documentation](INTEGRATION_TESTS_ARCHITECTURE.md)
- [Sample Service API](SAMPLE_SERVICE_API.md)
- [Troubleshooting Guide](INTEGRATION_TESTS_TROUBLESHOOTING.md)
- [EMF Platform Documentation](emf-docs/)

## Support

For issues or questions:
1. Check the [Troubleshooting Guide](INTEGRATION_TESTS_TROUBLESHOOTING.md)
2. Review service logs: `docker-compose logs <service-name>`
3. Open an issue in the appropriate repository
4. Contact the EMF platform team

## License

See individual repository LICENSE files for details.
