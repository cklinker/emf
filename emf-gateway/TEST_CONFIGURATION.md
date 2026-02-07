# Gateway Test Configuration

## Overview

The gateway project has two types of tests:
- **Unit Tests**: Fast tests that don't require external services
- **Integration Tests**: Tests that require the full Docker environment (Gateway, Control Plane, Keycloak, Redis, Kafka)

## Running Tests

### Unit Tests Only (Default)

```bash
mvn test -f emf-gateway/pom.xml
# or
mvn verify -f emf-gateway/pom.xml
```

This runs only unit tests and skips integration tests. Integration tests are excluded by:
1. JUnit `@Tag("integration")` annotation
2. File pattern: `**/*IntegrationTest.java`
3. Package pattern: `**/integration/**/*Test.java`

### Integration Tests Only

```bash
mvn verify -f emf-gateway/pom.xml -Pintegration-tests
```

**Prerequisites**: Docker services must be running:
```bash
docker-compose up -d
./scripts/wait-for-services.sh
```

### All Tests

```bash
# Start Docker services first
docker-compose up -d
./scripts/wait-for-services.sh

# Run all tests
mvn verify -f emf-gateway/pom.xml -Pintegration-tests -DskipITs=false
```

## Test Organization

### Unit Tests
Located in: `src/test/java/com/emf/gateway/**/*Test.java`
- Excludes: `**/integration/**`
- Fast execution (< 30 seconds total)
- No external dependencies
- Uses mocks and test doubles

### Integration Tests
Located in: `src/test/java/com/emf/gateway/integration/**/*Test.java`
- Requires Docker services
- Tests full request/response flows
- Validates authentication, authorization, routing, caching
- Execution time: 2-5 minutes

All integration tests either:
1. End with `IntegrationTest.java` naming convention
2. Extend `IntegrationTestBase` (which has `@Tag("integration")`)
3. Are located in the `integration` package

## Maven Configuration

### Surefire Plugin (Unit Tests)
- Excludes tests tagged with `@Tag("integration")`
- Excludes `**/*IntegrationTest.java`
- Excludes `**/integration/**/*Test.java`
- Runs during `mvn test` phase
- Parallel execution enabled (4 threads)

### Failsafe Plugin (Integration Tests)
- Includes tests tagged with `@Tag("integration")`
- Includes `**/*IntegrationTest.java`
- Includes `**/integration/**/*Test.java`
- Runs during `mvn verify` phase
- Skipped by default (requires `-Pintegration-tests` profile)

## Troubleshooting

### Tests Hang
If tests hang, it's likely integration tests are being run without Docker services:
1. Check if Docker services are running: `docker ps`
2. Verify integration tests are excluded: `mvn test -X` (look for excluded patterns)
3. Ensure test classes in `integration` package have `@Tag("integration")`

### Integration Tests Not Running
If integration tests are skipped when you want to run them:
1. Use the profile: `-Pintegration-tests`
2. Ensure Docker services are running
3. Check test class has `@Tag("integration")` or extends `IntegrationTestBase`

### Test Failures
- Unit test failures: Fix the code or test logic
- Integration test failures: Check Docker service health, logs, and configuration
