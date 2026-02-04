#!/bin/bash
# Test script to verify docker-compose configuration for integration testing
# This script validates the docker-compose.yml configuration without starting all services

set -e

echo "=========================================="
echo "Docker Compose Configuration Test"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print success message
success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Function to print error message
error() {
    echo -e "${RED}✗${NC} $1"
}

# Function to print warning message
warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Test 1: Validate docker-compose.yml syntax
echo "Test 1: Validating docker-compose.yml syntax..."
if docker-compose config --quiet; then
    success "docker-compose.yml syntax is valid"
else
    error "docker-compose.yml syntax is invalid"
    exit 1
fi
echo ""

# Test 2: Check all required services are defined
echo "Test 2: Checking required services are defined..."
REQUIRED_SERVICES=("postgres" "redis" "kafka" "keycloak" "emf-control-plane" "emf-gateway" "sample-service")
DEFINED_SERVICES=$(docker-compose config --services)

for service in "${REQUIRED_SERVICES[@]}"; do
    if echo "$DEFINED_SERVICES" | grep -q "^${service}$"; then
        success "Service '$service' is defined"
    else
        error "Service '$service' is NOT defined"
        exit 1
    fi
done
echo ""

# Test 3: Verify health checks are configured
echo "Test 3: Verifying health checks are configured..."
HEALTH_CHECK_SERVICES=("postgres" "redis" "kafka" "keycloak" "emf-control-plane" "emf-gateway" "sample-service")

for service in "${HEALTH_CHECK_SERVICES[@]}"; do
    if docker-compose config | grep -A 20 "^  ${service}:" | grep -q "healthcheck:"; then
        success "Service '$service' has health check configured"
    else
        warning "Service '$service' does NOT have health check configured"
    fi
done
echo ""

# Test 4: Verify service dependencies
echo "Test 4: Verifying service dependencies..."

# Check emf-control-plane depends on postgres and kafka
if docker-compose config | grep -A 20 "^  emf-control-plane:" | grep -A 10 "depends_on:" | grep -q "postgres:"; then
    success "emf-control-plane depends on postgres"
else
    error "emf-control-plane does NOT depend on postgres"
    exit 1
fi

if docker-compose config | grep -A 20 "^  emf-control-plane:" | grep -A 10 "depends_on:" | grep -q "kafka:"; then
    success "emf-control-plane depends on kafka"
else
    error "emf-control-plane does NOT depend on kafka"
    exit 1
fi

# Check emf-gateway depends on emf-control-plane, redis, kafka, and keycloak
if docker-compose config | grep -A 30 "^  emf-gateway:" | grep -A 20 "depends_on:" | grep -q "emf-control-plane:"; then
    success "emf-gateway depends on emf-control-plane"
else
    error "emf-gateway does NOT depend on emf-control-plane"
    exit 1
fi

if docker-compose config | grep -A 30 "^  emf-gateway:" | grep -A 20 "depends_on:" | grep -q "redis:"; then
    success "emf-gateway depends on redis"
else
    error "emf-gateway does NOT depend on redis"
    exit 1
fi

# Check sample-service depends on postgres, redis, and emf-control-plane
if docker-compose config | grep -A 30 "^  sample-service:" | grep -A 20 "depends_on:" | grep -q "postgres:"; then
    success "sample-service depends on postgres"
else
    error "sample-service does NOT depend on postgres"
    exit 1
fi

if docker-compose config | grep -A 30 "^  sample-service:" | grep -A 20 "depends_on:" | grep -q "redis:"; then
    success "sample-service depends on redis"
else
    error "sample-service does NOT depend on redis"
    exit 1
fi

if docker-compose config | grep -A 30 "^  sample-service:" | grep -A 20 "depends_on:" | grep -q "emf-control-plane:"; then
    success "sample-service depends on emf-control-plane"
else
    error "sample-service does NOT depend on emf-control-plane"
    exit 1
fi
echo ""

# Test 5: Verify network configuration
echo "Test 5: Verifying network configuration..."
if docker-compose config | grep -q "emf-network:"; then
    success "Network 'emf-network' is defined"
else
    error "Network 'emf-network' is NOT defined"
    exit 1
fi
echo ""

# Test 6: Verify port mappings
echo "Test 6: Verifying port mappings..."
EXPECTED_PORTS=(
    "5432:5432"  # postgres
    "6379:6379"  # redis
    "9094:9094"  # kafka
    "8180:8180"  # keycloak
    "8081:8080"  # emf-control-plane
    "8080:8080"  # emf-gateway
    "8082:8080"  # sample-service
)

for port_mapping in "${EXPECTED_PORTS[@]}"; do
    if docker-compose config | grep -q "\"${port_mapping}\""; then
        success "Port mapping '$port_mapping' is configured"
    else
        warning "Port mapping '$port_mapping' is NOT configured"
    fi
done
echo ""

# Test 7: Check Dockerfiles exist
echo "Test 7: Checking Dockerfiles exist..."
if [ -f "emf-control-plane/Dockerfile" ]; then
    success "emf-control-plane/Dockerfile exists"
else
    error "emf-control-plane/Dockerfile does NOT exist"
    exit 1
fi

if [ -f "emf-gateway/Dockerfile" ]; then
    success "emf-gateway/Dockerfile exists"
else
    error "emf-gateway/Dockerfile does NOT exist"
    exit 1
fi

if [ -f "integration-tests/sample-service/Dockerfile" ]; then
    success "integration-tests/sample-service/Dockerfile exists"
else
    warning "integration-tests/sample-service/Dockerfile does NOT exist (will be created in Task 4)"
fi
echo ""

echo "=========================================="
echo "Configuration Test Summary"
echo "=========================================="
success "All critical docker-compose configuration tests passed!"
echo ""
echo "Note: The sample-service will be implemented in Task 4."
echo "      Infrastructure services (postgres, redis, kafka, keycloak) are already configured."
echo "      Platform services (emf-control-plane, emf-gateway) are configured and ready."
echo ""
