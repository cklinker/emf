# Integration Tests Troubleshooting Guide

This guide provides solutions to common issues encountered when running the EMF integration test suite.

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Service Startup Issues](#service-startup-issues)
- [Test Execution Issues](#test-execution-issues)
- [Authentication Issues](#authentication-issues)
- [Database Issues](#database-issues)
- [Cache Issues](#cache-issues)
- [Kafka Issues](#kafka-issues)
- [Network Issues](#network-issues)
- [Performance Issues](#performance-issues)
- [Docker Issues](#docker-issues)
- [Common Error Messages](#common-error-messages)
- [Debugging Commands](#debugging-commands)

## Quick Diagnostics

Run these commands first to identify the problem area:

```bash
# Check all service status
docker-compose ps

# Check service health
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Control Plane
curl http://localhost:8082/actuator/health  # Sample Service

# Check logs for errors
docker-compose logs --tail=50

# Check resource usage
docker stats --no-stream

# Check disk space
df -h
```

## Service Startup Issues

### Problem: Services Won't Start

**Symptoms**:
- `docker-compose up` hangs
- Services show "Exited" status
- Health checks never pass

**Solutions**:

1. **Check logs for specific service**:
```bash
docker-compose logs <service-name>
```

2. **Check port conflicts**:
```bash
# Check if ports are already in use
lsof -i :8080  # Gateway
lsof -i :8081  # Control Plane
lsof -i :8082  # Sample Service
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9092  # Kafka
lsof -i :8180  # Keycloak

# Kill conflicting processes
kill -9 <PID>
```

3. **Rebuild containers**:
```bash
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

4. **Check Docker resources**:
```bash
# Increase Docker Desktop resources
# Settings → Resources → Increase CPU/Memory to at least:
# - CPUs: 4
# - Memory: 8GB
```

### Problem: PostgreSQL Won't Start

**Symptoms**:
- PostgreSQL container exits immediately
- Error: "database system was interrupted"

**Solutions**:

1. **Remove corrupted volume**:
```bash
docker-compose down -v
docker volume rm $(docker volume ls -q | grep postgres)
docker-compose up -d postgres
```

2. **Check initialization script**:
```bash
# Verify init.sql syntax
cat docker/postgres/init.sql

# Check PostgreSQL logs
docker-compose logs postgres
```

3. **Connect manually to debug**:
```bash
docker exec -it postgres psql -U emf -d emf_control_plane
```

### Problem: Keycloak Won't Start

**Symptoms**:
- Keycloak container restarts repeatedly
- Error: "Failed to start Keycloak"

**Solutions**:

1. **Check realm configuration**:
```bash
# Verify realm JSON is valid
cat docker/keycloak/emf-realm.json | jq

# Check Keycloak logs
docker-compose logs keycloak
```

2. **Reset Keycloak**:
```bash
docker-compose down
docker volume rm $(docker volume ls -q | grep keycloak)
docker-compose up -d keycloak
```

3. **Verify realm import**:
```bash
# Wait for Keycloak to start
sleep 30

# Test realm endpoint
curl http://localhost:8180/realms/emf/.well-known/openid-configuration
```

### Problem: Kafka Won't Start

**Symptoms**:
- Kafka container exits with error
- Error: "Kafka server failed to start"

**Solutions**:

1. **Check Kafka logs**:
```bash
docker-compose logs kafka
```

2. **Reset Kafka**:
```bash
docker-compose down
docker volume rm $(docker volume ls -q | grep kafka)
docker-compose up -d kafka
```

3. **Verify Kafka is accessible**:
```bash
# List topics
docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Problem: Control Plane Won't Start

**Symptoms**:
- Control Plane health check fails
- Error: "Failed to connect to database"

**Solutions**:

1. **Check database connection**:
```bash
# Verify PostgreSQL is running
docker-compose ps postgres

# Check Control Plane logs
docker-compose logs emf-control-plane
```

2. **Verify database schema**:
```bash
docker exec -it postgres psql -U emf -d emf_control_plane -c "\dt"
```

3. **Check application configuration**:
```bash
# View environment variables
docker-compose exec emf-control-plane env | grep SPRING
```

### Problem: Gateway Won't Start

**Symptoms**:
- Gateway health check fails
- Error: "Failed to bootstrap configuration"

**Solutions**:

1. **Check Control Plane is running**:
```bash
curl http://localhost:8081/actuator/health
```

2. **Check bootstrap endpoint**:
```bash
# Get service account token (if needed)
TOKEN=$(curl -s -X POST http://localhost:8180/realms/emf/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=gateway-service" \
  -d "client_secret=gateway-secret" | jq -r '.access_token')

# Test bootstrap endpoint
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/control/gateway/bootstrap
```

3. **Check Gateway logs**:
```bash
docker-compose logs emf-gateway
```

### Problem: Sample Service Won't Start

**Symptoms**:
- Sample Service health check fails
- Error: "Failed to initialize collections"

**Solutions**:

1. **Check database connection**:
```bash
docker-compose logs sample-service | grep -i database
```

2. **Verify table creation**:
```bash
docker exec -it postgres psql -U emf -d emf_control_plane -c "\dt"
# Should show: projects, tasks tables
```

3. **Check service registration**:
```bash
docker-compose logs sample-service | grep -i "register"
```

## Test Execution Issues

### Problem: Tests Fail to Start

**Symptoms**:
- Maven build fails
- Error: "Cannot connect to services"

**Solutions**:

1. **Verify all services are healthy**:
```bash
./scripts/wait-for-services.sh
```

2. **Check test configuration**:
```bash
# Verify test properties
cat emf-gateway/src/test/resources/application-test.yml
```

3. **Run tests with verbose logging**:
```bash
./scripts/run-integration-tests.sh --verbose
```

### Problem: Tests Timeout

**Symptoms**:
- Tests hang indefinitely
- Error: "Timeout waiting for response"

**Solutions**:

1. **Increase timeout values**:
```java
// In test class
@BeforeAll
public static void waitForServices() {
    Awaitility.await()
        .atMost(Duration.ofMinutes(5))  // Increase from 2 to 5
        .pollInterval(Duration.ofSeconds(10))
        .until(() -> allServicesHealthy());
}
```

2. **Check service responsiveness**:
```bash
# Test each service manually
time curl http://localhost:8080/actuator/health
time curl http://localhost:8081/actuator/health
time curl http://localhost:8082/actuator/health
```

3. **Check system resources**:
```bash
docker stats
top
```

### Problem: Tests Fail Intermittently

**Symptoms**:
- Tests pass sometimes, fail other times
- Error: "Connection refused" or "Resource not found"

**Solutions**:

1. **Add retry logic**:
```java
@Test
@RepeatedTest(3)  // Retry up to 3 times
void testFeature() {
    // Test logic
}
```

2. **Increase wait times**:
```java
// Wait for eventual consistency
Thread.sleep(1000);  // Wait 1 second

// Or use Awaitility
await().atMost(5, SECONDS)
    .until(() -> resourceExists(id));
```

3. **Check for race conditions**:
```bash
# Run tests sequentially
mvn test -DforkCount=1
```

### Problem: Property Tests Fail

**Symptoms**:
- Property-based tests fail with counterexample
- Error: "Property violated for input: ..."

**Solutions**:

1. **Analyze the counterexample**:
```java
// jqwik will show the failing input
// Example: "Property violated for input: Project(name='', description='test', status='ACTIVE')"
// This shows empty name is the issue
```

2. **Fix the generator**:
```java
@Provide
Arbitrary<Project> validProjects() {
    return Combinators.combine(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),  // Ensure min length
        Arbitraries.strings().ofMaxLength(500),
        Arbitraries.of("PLANNING", "ACTIVE", "COMPLETED", "ARCHIVED")
    ).as((name, description, status) -> 
        new Project(name, description, status)
    );
}
```

3. **Reduce iterations for debugging**:
```java
@Property(tries = 10)  // Reduce from 100 to 10 for faster debugging
void testProperty(@ForAll("validProjects") Project project) {
    // Test logic
}
```

## Authentication Issues

### Problem: Cannot Get Token from Keycloak

**Symptoms**:
- Token request returns 401
- Error: "Invalid credentials"

**Solutions**:

1. **Verify Keycloak is running**:
```bash
curl http://localhost:8180/realms/emf/.well-known/openid-configuration
```

2. **Check user credentials**:
```bash
# Test with admin user
curl -X POST http://localhost:8180/realms/emf/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=emf-client" \
  -d "username=admin" \
  -d "password=admin"
```

3. **Verify realm configuration**:
```bash
# Check if realm was imported
docker-compose logs keycloak | grep -i "realm"

# Verify users exist
# Login to Keycloak admin console: http://localhost:8180
# Username: admin, Password: admin
```

4. **Re-import realm**:
```bash
docker-compose down
docker volume rm $(docker volume ls -q | grep keycloak)
docker-compose up -d keycloak
./scripts/test-keycloak-realm.sh
```

### Problem: Token Validation Fails

**Symptoms**:
- Gateway returns 401 with valid token
- Error: "Invalid token signature"

**Solutions**:

1. **Check token expiration**:
```bash
# Decode JWT token
echo "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." | cut -d. -f2 | base64 -d | jq

# Check 'exp' claim
```

2. **Verify issuer URL**:
```bash
# Check Gateway configuration
docker-compose exec emf-gateway env | grep ISSUER_URI

# Should match Keycloak realm URL
# http://keycloak:8180/realms/emf
```

3. **Check JWKS endpoint**:
```bash
curl http://localhost:8180/realms/emf/protocol/openid-connect/certs
```

### Problem: Wrong User Roles

**Symptoms**:
- User has token but gets 403 Forbidden
- Error: "Insufficient permissions"

**Solutions**:

1. **Decode token and check roles**:
```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:8180/realms/emf/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=emf-client" \
  -d "username=admin" \
  -d "password=admin" | jq -r '.access_token')

# Decode and check roles
echo $TOKEN | cut -d. -f2 | base64 -d | jq '.realm_access.roles'
```

2. **Update user roles in Keycloak**:
```bash
# Login to Keycloak admin console
# Navigate to: Realm → Users → Select user → Role Mappings
# Add required roles: ADMIN, USER
```

## Database Issues

### Problem: Connection Refused

**Symptoms**:
- Services can't connect to PostgreSQL
- Error: "Connection refused to localhost:5432"

**Solutions**:

1. **Check PostgreSQL is running**:
```bash
docker-compose ps postgres
docker-compose logs postgres
```

2. **Test connection**:
```bash
docker exec -it postgres psql -U emf -d emf_control_plane -c "SELECT 1"
```

3. **Check connection string**:
```bash
# Should use service name, not localhost
# Correct: jdbc:postgresql://postgres:5432/emf_control_plane
# Wrong: jdbc:postgresql://localhost:5432/emf_control_plane
```

### Problem: Tables Not Created

**Symptoms**:
- Error: "relation 'projects' does not exist"
- Sample service fails to start

**Solutions**:

1. **Check table creation logs**:
```bash
docker-compose logs sample-service | grep -i "table"
```

2. **Verify tables exist**:
```bash
docker exec -it postgres psql -U emf -d emf_control_plane -c "\dt"
```

3. **Manually create tables**:
```bash
# Connect to database
docker exec -it postgres psql -U emf -d emf_control_plane

# Check if runtime-core created tables
\dt

# If not, check for errors in sample service logs
```

4. **Reset database**:
```bash
docker-compose down
docker volume rm $(docker volume ls -q | grep postgres)
docker-compose up -d postgres
# Wait for PostgreSQL to be ready
sleep 10
docker-compose up -d sample-service
```

### Problem: Data Inconsistency

**Symptoms**:
- Tests fail with unexpected data
- Resources from previous tests still exist

**Solutions**:

1. **Clean database between tests**:
```bash
# Stop all services
docker-compose down

# Remove volumes
docker-compose down -v

# Restart
docker-compose up -d
```

2. **Implement proper cleanup**:
```java
@AfterEach
void cleanup() {
    testDataHelper.deleteAllTestProjects();
    testDataHelper.deleteAllTestTasks();
}
```

## Cache Issues

### Problem: Redis Connection Failed

**Symptoms**:
- Error: "Cannot connect to Redis"
- Include processing doesn't work

**Solutions**:

1. **Check Redis is running**:
```bash
docker-compose ps redis
docker-compose logs redis
```

2. **Test Redis connection**:
```bash
docker exec -it redis redis-cli ping
# Should return: PONG
```

3. **Check Redis configuration**:
```bash
# Verify connection string
docker-compose exec sample-service env | grep REDIS
# Should be: redis:6379, not localhost:6379
```

### Problem: Cache Not Working

**Symptoms**:
- Include parameter doesn't return related resources
- Resources not found in cache

**Solutions**:

1. **Check cached keys**:
```bash
docker exec -it redis redis-cli KEYS "jsonapi:*"
```

2. **Verify caching logic**:
```bash
# Check sample service logs for cache operations
docker-compose logs sample-service | grep -i cache
```

3. **Test cache manually**:
```bash
# Set a test key
docker exec -it redis redis-cli SET test:key "test value"

# Get the key
docker exec -it redis redis-cli GET test:key

# Check TTL
docker exec -it redis redis-cli TTL test:key
```

4. **Clear cache**:
```bash
docker exec -it redis redis-cli FLUSHALL
```

### Problem: Cache Invalidation Not Working

**Symptoms**:
- Updated resources still return old data
- Deleted resources still in cache

**Solutions**:

1. **Check event listener**:
```bash
docker-compose logs sample-service | grep -i "event"
```

2. **Verify cache keys are deleted**:
```bash
# Before delete
docker exec -it redis redis-cli GET "jsonapi:projects:123"

# After delete (should return nil)
docker exec -it redis redis-cli GET "jsonapi:projects:123"
```

3. **Restart sample service**:
```bash
docker-compose restart sample-service
```

## Kafka Issues

### Problem: Kafka Not Starting

**Symptoms**:
- Kafka container exits
- Error: "Kafka server failed to start"

**Solutions**:

1. **Check Kafka logs**:
```bash
docker-compose logs kafka | tail -100
```

2. **Reset Kafka**:
```bash
docker-compose down
docker volume rm $(docker volume ls -q | grep kafka)
docker-compose up -d kafka
```

3. **Verify Kafka is in KRaft mode**:
```bash
docker-compose logs kafka | grep -i kraft
```

### Problem: Events Not Published

**Symptoms**:
- Configuration changes don't propagate
- Gateway doesn't receive events

**Solutions**:

1. **Check topics exist**:
```bash
docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

2. **Check producer logs**:
```bash
docker-compose logs emf-control-plane | grep -i kafka
```

3. **Manually consume events**:
```bash
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic collection-changes \
  --from-beginning
```

### Problem: Events Not Consumed

**Symptoms**:
- Gateway doesn't update configuration
- Events published but not processed

**Solutions**:

1. **Check consumer group**:
```bash
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group gateway-consumer-group
```

2. **Check Gateway consumer logs**:
```bash
docker-compose logs emf-gateway | grep -i kafka
```

3. **Reset consumer offset**:
```bash
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group gateway-consumer-group \
  --reset-offsets \
  --to-earliest \
  --topic collection-changes \
  --execute
```

## Network Issues

### Problem: Services Can't Communicate

**Symptoms**:
- Error: "Connection refused"
- Services can't reach each other

**Solutions**:

1. **Check Docker network**:
```bash
docker network ls
docker network inspect emf-network
```

2. **Test connectivity**:
```bash
# From gateway to control plane
docker-compose exec emf-gateway curl http://emf-control-plane:8080/actuator/health

# From sample service to postgres
docker-compose exec sample-service nc -zv postgres 5432
```

3. **Recreate network**:
```bash
docker-compose down
docker network rm emf-network
docker-compose up -d
```

### Problem: Cannot Access Services from Host

**Symptoms**:
- curl to localhost:8080 fails
- Tests can't connect to services

**Solutions**:

1. **Check port mappings**:
```bash
docker-compose ps
# Verify ports are mapped correctly
```

2. **Check firewall**:
```bash
# macOS
sudo pfctl -s all

# Linux
sudo iptables -L
```

3. **Test with container IP**:
```bash
# Get container IP
docker inspect emf-gateway | grep IPAddress

# Test with IP
curl http://<container-ip>:8080/actuator/health
```

## Performance Issues

### Problem: Tests Run Slowly

**Symptoms**:
- Test suite takes > 10 minutes
- Individual tests timeout

**Solutions**:

1. **Check resource usage**:
```bash
docker stats
top
```

2. **Increase Docker resources**:
```bash
# Docker Desktop → Settings → Resources
# Increase CPU to 4+ cores
# Increase Memory to 8+ GB
```

3. **Reduce test iterations**:
```java
@Property(tries = 10)  // Reduce from 100
void testProperty() {
    // Test logic
}
```

4. **Run tests in parallel**:
```bash
mvn test -DforkCount=2
```

5. **Clean up Docker**:
```bash
docker system prune -a
docker volume prune
```

### Problem: High Memory Usage

**Symptoms**:
- Docker uses > 8GB memory
- System becomes unresponsive

**Solutions**:

1. **Check memory usage per container**:
```bash
docker stats --no-stream
```

2. **Limit container memory**:
```yaml
# In docker-compose.yml
services:
  emf-gateway:
    mem_limit: 1g
```

3. **Reduce JVM heap size**:
```yaml
# In docker-compose.yml
services:
  emf-gateway:
    environment:
      JAVA_OPTS: "-Xmx512m -Xms256m"
```

### Problem: High CPU Usage

**Symptoms**:
- Docker uses 100% CPU
- Tests run very slowly

**Solutions**:

1. **Check CPU usage per container**:
```bash
docker stats --no-stream
```

2. **Limit container CPU**:
```yaml
# In docker-compose.yml
services:
  emf-gateway:
    cpus: 1.0
```

3. **Check for infinite loops**:
```bash
# Check logs for repeated errors
docker-compose logs --tail=100
```

## Docker Issues

### Problem: Docker Daemon Not Running

**Symptoms**:
- Error: "Cannot connect to Docker daemon"

**Solutions**:

1. **Start Docker**:
```bash
# macOS
open -a Docker

# Linux
sudo systemctl start docker
```

2. **Check Docker status**:
```bash
docker info
```

### Problem: Out of Disk Space

**Symptoms**:
- Error: "no space left on device"
- Cannot create containers

**Solutions**:

1. **Check disk usage**:
```bash
df -h
docker system df
```

2. **Clean up Docker**:
```bash
# Remove unused containers
docker container prune

# Remove unused images
docker image prune -a

# Remove unused volumes
docker volume prune

# Remove everything
docker system prune -a --volumes
```

3. **Increase Docker disk space**:
```bash
# Docker Desktop → Settings → Resources → Disk image size
```

### Problem: Build Failures

**Symptoms**:
- docker-compose build fails
- Error: "failed to solve with frontend dockerfile.v0"

**Solutions**:

1. **Clear build cache**:
```bash
docker-compose build --no-cache
```

2. **Check Dockerfile syntax**:
```bash
cat sample-service/Dockerfile
```

3. **Build individually**:
```bash
docker build -t sample-service ./sample-service
```

## Common Error Messages

| Error Message | Cause | Solution |
|--------------|-------|----------|
| "Connection refused" | Service not started or wrong host | Check service status, use service name not localhost |
| "401 Unauthorized" | Missing/invalid token | Get new token from Keycloak |
| "403 Forbidden" | Insufficient permissions | Check user roles in token |
| "404 Not Found" | Resource doesn't exist or route not registered | Verify resource exists, check service registration |
| "500 Internal Server Error" | Backend failure | Check service logs for stack trace |
| "Timeout waiting for services" | Services slow to start | Increase timeout, check resources |
| "relation does not exist" | Database table not created | Check sample service initialization |
| "Cannot connect to Redis" | Redis not running or wrong host | Check Redis status, verify connection string |
| "Failed to publish event" | Kafka not running | Check Kafka status |
| "Invalid JSON" | Malformed request body | Validate JSON syntax |

## Debugging Commands

### Service Health

```bash
# Check all services
docker-compose ps

# Check specific service health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health

# Check service logs
docker-compose logs <service-name>
docker-compose logs --tail=100 <service-name>
docker-compose logs -f <service-name>  # Follow logs
```

### Database

```bash
# Connect to PostgreSQL
docker exec -it postgres psql -U emf -d emf_control_plane

# List tables
\dt

# Describe table
\d projects

# Query data
SELECT * FROM projects;

# Check connections
SELECT * FROM pg_stat_activity;
```

### Redis

```bash
# Connect to Redis
docker exec -it redis redis-cli

# Check all keys
KEYS *

# Get specific key
GET jsonapi:projects:123

# Check TTL
TTL jsonapi:projects:123

# Clear all data
FLUSHALL
```

### Kafka

```bash
# List topics
docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Describe topic
docker exec -it kafka kafka-topics.sh --describe --topic collection-changes --bootstrap-server localhost:9092

# Consume messages
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic collection-changes \
  --from-beginning

# Check consumer groups
docker exec -it kafka kafka-consumer-groups.sh --list --bootstrap-server localhost:9092
```

### Network

```bash
# List networks
docker network ls

# Inspect network
docker network inspect emf-network

# Test connectivity
docker-compose exec emf-gateway ping postgres
docker-compose exec emf-gateway curl http://emf-control-plane:8080/actuator/health
```

### Performance

```bash
# Resource usage
docker stats --no-stream

# System info
docker info

# Disk usage
docker system df

# Container processes
docker-compose top
```

## Getting Help

If you're still stuck after trying these solutions:

1. **Collect diagnostic information**:
```bash
# Save all logs
docker-compose logs > all-logs.txt

# Save service status
docker-compose ps > service-status.txt

# Save resource usage
docker stats --no-stream > resource-usage.txt
```

2. **Check documentation**:
- [Integration Tests README](INTEGRATION_TESTS_README.md)
- [Architecture Documentation](INTEGRATION_TESTS_ARCHITECTURE.md)
- [Sample Service API](SAMPLE_SERVICE_API.md)

3. **Open an issue**:
- Include error messages
- Include relevant logs
- Include steps to reproduce
- Include system information (OS, Docker version, etc.)

4. **Contact the team**:
- Provide diagnostic files
- Describe what you've tried
- Include any error messages
