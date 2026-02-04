#!/bin/bash

# Script to verify the sample service checkpoint
# This script:
# 1. Starts the Docker environment
# 2. Waits for all services to be healthy
# 3. Verifies the sample service health check
# 4. Verifies service registration with control plane
# 5. Verifies database tables are created

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Service URLs
CONTROL_PLANE_URL="http://localhost:8081"
GATEWAY_URL="http://localhost:8080"
SAMPLE_SERVICE_URL="http://localhost:8082"

echo "=========================================="
echo "Sample Service Verification"
echo "=========================================="
echo ""

# Function to check if a service is healthy
check_health() {
    local service_name=$1
    local url=$2
    local max_attempts=30
    local attempt=1
    
    echo -n "Checking $service_name health..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$url/actuator/health" > /dev/null 2>&1; then
            echo -e " ${GREEN}✓ Healthy${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo -e " ${RED}✗ Failed${NC}"
    return 1
}

# Function to check if Docker service is running
check_docker_service() {
    local service_name=$1
    
    if docker ps --filter "name=$service_name" --filter "status=running" | grep -q "$service_name"; then
        echo -e "${GREEN}✓${NC} $service_name is running"
        return 0
    else
        echo -e "${RED}✗${NC} $service_name is not running"
        return 1
    fi
}

# Step 1: Check if Docker is running
echo "Step 1: Checking Docker..."
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✗ Docker is not running${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}"
echo ""

# Step 2: Start Docker environment
echo "Step 2: Starting Docker environment..."
echo "This may take a few minutes on first run..."
docker-compose up -d

echo ""
echo "Waiting for services to start..."
sleep 10
echo ""

# Step 3: Check Docker services are running
echo "Step 3: Checking Docker services..."
check_docker_service "emf-postgres"
check_docker_service "emf-redis"
check_docker_service "emf-kafka"
check_docker_service "emf-keycloak"
check_docker_service "emf-control-plane"
check_docker_service "emf-gateway"
check_docker_service "emf-sample-service"
echo ""

# Step 4: Check service health endpoints
echo "Step 4: Checking service health endpoints..."
check_health "Control Plane" "$CONTROL_PLANE_URL"
check_health "Gateway" "$GATEWAY_URL"
check_health "Sample Service" "$SAMPLE_SERVICE_URL"
echo ""

# Step 5: Verify service registration with control plane
echo "Step 5: Verifying service registration..."
echo -n "Checking if sample-service is registered..."
sleep 5  # Give it a moment to register

# Try to get services from control plane
if curl -sf "$CONTROL_PLANE_URL/control/services" > /dev/null 2>&1; then
    services=$(curl -s "$CONTROL_PLANE_URL/control/services")
    if echo "$services" | grep -q "sample-service"; then
        echo -e " ${GREEN}✓ Registered${NC}"
    else
        echo -e " ${YELLOW}⚠ Not found in services list${NC}"
        echo "Services response: $services"
    fi
else
    echo -e " ${YELLOW}⚠ Could not query control plane services${NC}"
fi
echo ""

# Step 6: Verify database tables are created
echo "Step 6: Verifying database tables..."
echo -n "Checking if projects table exists..."
if docker exec emf-postgres psql -U emf -d emf_control_plane -c "\dt projects" 2>/dev/null | grep -q "projects"; then
    echo -e " ${GREEN}✓ Exists${NC}"
else
    echo -e " ${YELLOW}⚠ Not found${NC}"
fi

echo -n "Checking if tasks table exists..."
if docker exec emf-postgres psql -U emf -d emf_control_plane -c "\dt tasks" 2>/dev/null | grep -q "tasks"; then
    echo -e " ${GREEN}✓ Exists${NC}"
else
    echo -e " ${YELLOW}⚠ Not found${NC}"
fi
echo ""

# Step 7: Test sample service endpoints
echo "Step 7: Testing sample service endpoints..."
echo -n "Testing GET /api/collections/projects..."
if curl -sf "$SAMPLE_SERVICE_URL/api/collections/projects" > /dev/null 2>&1; then
    echo -e " ${GREEN}✓ Accessible${NC}"
else
    echo -e " ${YELLOW}⚠ Not accessible${NC}"
fi

echo -n "Testing GET /api/collections/tasks..."
if curl -sf "$SAMPLE_SERVICE_URL/api/collections/tasks" > /dev/null 2>&1; then
    echo -e " ${GREEN}✓ Accessible${NC}"
else
    echo -e " ${YELLOW}⚠ Not accessible${NC}"
fi
echo ""

# Step 8: Check logs for errors
echo "Step 8: Checking sample service logs for errors..."
if docker logs emf-sample-service 2>&1 | grep -i "error" | grep -v "ERROR_LEVEL" | head -5; then
    echo -e "${YELLOW}⚠ Found errors in logs (see above)${NC}"
else
    echo -e "${GREEN}✓ No errors found in logs${NC}"
fi
echo ""

echo "=========================================="
echo "Verification Complete!"
echo "=========================================="
echo ""
echo "To view logs:"
echo "  docker logs emf-sample-service"
echo ""
echo "To stop services:"
echo "  docker-compose down"
echo ""
echo "To view all running services:"
echo "  docker-compose ps"
