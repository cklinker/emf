# Integration Test Scripts

This directory contains scripts for running and managing the EMF platform integration tests.

## Main Scripts

### run-integration-tests.sh

The main script for running integration tests. It orchestrates the entire test lifecycle:

1. Starts Docker environment
2. Waits for all services to be healthy
3. Runs integration tests
4. Generates test reports (in CI mode)
5. Cleans up Docker environment

**Usage:**

```bash
# Run all tests
./scripts/run-integration-tests.sh

# Run specific test category
./scripts/run-integration-tests.sh --category authentication

# Run in watch mode for development
./scripts/run-integration-tests.sh --watch

# Run in CI mode with reports
./scripts/run-integration-tests.sh --ci

# Run without cleanup (keep Docker running)
./scripts/run-integration-tests.sh --no-cleanup

# Enable verbose output
./scripts/run-integration-tests.sh --verbose
```

**Options:**

- `-c, --category CATEGORY` - Run specific test category (default: all)
  - Valid categories: `all`, `infrastructure`, `authentication`, `authorization`, `crud`, `relationships`, `includes`, `cache`, `events`, `errors`, `e2e`
- `-w, --watch` - Run tests in watch mode (re-run on file changes)
- `--ci` - Run in CI mode (generate JUnit XML reports, collect logs on failure)
- `--html` - Generate HTML test reports after test execution
- `--no-cleanup` - Don't clean up Docker environment after tests
- `-v, --verbose` - Enable verbose output
- `-h, --help` - Show help message

**Environment Variables:**

- `CLEANUP` - Set to `false` to skip cleanup (default: `true`)
- `VERBOSE` - Set to `true` for verbose output (default: `false`)
- `CATEGORY` - Test category to run (default: `all`)
- `WATCH` - Set to `true` for watch mode (default: `false`)
- `CI_MODE` - Set to `true` for CI mode (default: `false`)

### wait-for-services.sh

Waits for all services to be healthy before proceeding. Used internally by `run-integration-tests.sh` but can also be used standalone.

**Usage:**

```bash
# Wait for all services with default timeout (5 minutes)
./scripts/wait-for-services.sh

# Wait with custom timeout
MAX_WAIT_TIME=600 ./scripts/wait-for-services.sh

# Wait with verbose output
VERBOSE=true ./scripts/wait-for-services.sh
```

**Environment Variables:**

- `MAX_WAIT_TIME` - Maximum time to wait in seconds (default: `300`)
- `CHECK_INTERVAL` - Time between health checks in seconds (default: `5`)
- `VERBOSE` - Set to `true` for verbose output (default: `false`)
- Service URLs can be customized:
  - `POSTGRES_HOST`, `POSTGRES_PORT`
  - `REDIS_HOST`, `REDIS_PORT`
  - `KAFKA_HOST`, `KAFKA_PORT`
  - `KEYCLOAK_URL`
  - `CONTROL_PLANE_URL`
  - `GATEWAY_URL`
  - `SAMPLE_SERVICE_URL`

## Utility Scripts

### generate-test-report.sh

Generates HTML test reports from JUnit XML files. Provides a visual representation of test results with detailed information about failures and execution times.

**Usage:**

```bash
# Generate HTML report from last test run
./scripts/generate-test-report.sh
```

The script will:
1. Generate HTML reports using Maven Surefire Report plugin
2. Generate code coverage reports using JaCoCo
3. Display a summary of test results
4. Optionally open the report in your browser

Reports are generated at:
- Test report: `emf-gateway/target/site/surefire-report.html`
- Coverage report: `emf-gateway/target/site/jacoco/index.html`

### monitor-test-performance.sh

Monitors and tracks test execution performance over time. Alerts when performance degrades beyond acceptable thresholds.

**Usage:**

```bash
# Monitor performance after test run
./scripts/monitor-test-performance.sh
```

The script will:
1. Extract test execution times from surefire reports
2. Save performance data to `.test-performance/performance-history.json`
3. Compare with previous runs and alert on degradation (>20% increase)
4. Generate a performance report showing trends

See [PERFORMANCE_MONITORING.md](./PERFORMANCE_MONITORING.md) for detailed documentation.

### test-docker-compose.sh

Validates the docker-compose.yml configuration without starting services.

**Usage:**

```bash
./scripts/test-docker-compose.sh
```

### verify-sample-service.sh

Verifies the sample service checkpoint by starting the environment and checking service health, registration, and database tables.

**Usage:**

```bash
./scripts/verify-sample-service.sh
```

### verify-sample-service-standalone.sh

Verifies the sample service in standalone mode (without other platform services).

**Usage:**

```bash
./scripts/verify-sample-service-standalone.sh
```

## Test Categories

The integration tests are organized into the following categories:

- **infrastructure** - Tests for basic infrastructure (health checks, Redis, Kafka, bootstrap)
  - `HealthCheckIntegrationTest`
  - `ControlPlaneBootstrapIntegrationTest`
  - `RedisCacheIntegrationTest`
  - `KafkaConfigurationUpdateIntegrationTest`

- **authentication** - Tests for JWT authentication
  - `AuthenticationIntegrationTest`

- **authorization** - Tests for authorization policies
  - `AuthorizationIntegrationTest`

- **crud** - Tests for CRUD operations on collections
  - `CollectionCrudIntegrationTest`

- **relationships** - Tests for related collections
  - `RelatedCollectionsIntegrationTest`

- **includes** - Tests for JSON:API include parameter
  - `IncludeParameterIntegrationTest`

- **cache** - Tests for Redis caching
  - `CacheIntegrationTest`

- **events** - Tests for Kafka event-driven configuration
  - `ConfigurationUpdateIntegrationTest`

- **errors** - Tests for error handling
  - `ErrorHandlingIntegrationTest`

- **e2e** - End-to-end flow tests
  - `EndToEndFlowIntegrationTest`
  - `EndToEndRequestFlowIntegrationTest`

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Integration Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run integration tests
        run: ./scripts/run-integration-tests.sh --ci
      
      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: test-reports/
      
      - name: Upload service logs
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: service-logs
          path: test-logs/
```

## Troubleshooting

### Services not starting

If services fail to start, check the logs:

```bash
docker-compose logs <service-name>
```

Common issues:
- Port conflicts: Ensure ports 5432, 6379, 8080, 8081, 8082, 8180, 9094 are available
- Memory: Ensure Docker has at least 4GB RAM allocated
- Disk space: Ensure sufficient disk space for Docker volumes

### Tests timing out

If tests timeout waiting for services:

```bash
# Increase wait time
MAX_WAIT_TIME=600 ./scripts/run-integration-tests.sh
```

### Tests failing

To debug failing tests:

```bash
# Run with verbose output
./scripts/run-integration-tests.sh --verbose --no-cleanup

# Check service logs
docker-compose logs emf-gateway
docker-compose logs emf-control-plane
docker-compose logs sample-service

# Run specific test category
./scripts/run-integration-tests.sh --category authentication --no-cleanup
```

### Cleanup issues

If Docker cleanup fails:

```bash
# Manual cleanup
docker-compose down -v
docker system prune -f
```

## Development Workflow

### Quick iteration

```bash
# Start services once
docker-compose up -d

# Run tests without cleanup
./scripts/run-integration-tests.sh --no-cleanup

# Make code changes...

# Re-run tests (services still running)
./scripts/run-integration-tests.sh --no-cleanup

# When done, cleanup manually
docker-compose down -v
```

### Watch mode

```bash
# Run tests in watch mode (re-runs on file changes)
./scripts/run-integration-tests.sh --watch
```

### Category-specific testing

```bash
# Work on authentication tests
./scripts/run-integration-tests.sh --category authentication --no-cleanup

# Work on CRUD tests
./scripts/run-integration-tests.sh --category crud --no-cleanup
```

## Performance Tips

1. **Keep services running** - Use `--no-cleanup` to avoid restarting services between test runs
2. **Run specific categories** - Use `--category` to run only the tests you're working on
3. **Use watch mode** - Use `--watch` for continuous testing during development
4. **Docker caching** - Docker will cache images after first build, subsequent runs are faster

## Requirements

- Docker and Docker Compose
- JDK 21
- Maven 3.9+
- Bash shell
- curl (for health checks)
- Optional: nc (netcat) for Kafka health checks
- Optional: pg_isready for PostgreSQL health checks
- Optional: redis-cli for Redis health checks
