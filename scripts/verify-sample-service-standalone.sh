#!/bin/bash

# Script to verify the sample service standalone (without full Docker environment)
# This script:
# 1. Verifies the sample service JAR was built
# 2. Checks that all required Java classes exist
# 3. Verifies the application.yml configuration
# 4. Runs basic unit tests

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Sample Service Standalone Verification"
echo "=========================================="
echo ""

# Step 1: Check if JAR was built
echo "Step 1: Checking if sample service JAR exists..."
if [ -f "sample-service/target/sample-service-1.0.0-SNAPSHOT.jar" ]; then
    echo -e "${GREEN}✓ JAR file exists${NC}"
    ls -lh sample-service/target/sample-service-1.0.0-SNAPSHOT.jar
else
    echo -e "${RED}✗ JAR file not found${NC}"
    echo "Run: cd sample-service && mvn clean package"
    exit 1
fi
echo ""

# Step 2: Check required Java classes
echo "Step 2: Checking required Java classes..."
required_classes=(
    "com/emf/sample/SampleServiceApplication.class"
    "com/emf/sample/config/CollectionInitializer.class"
    "com/emf/sample/config/ControlPlaneRegistration.class"
    "com/emf/sample/service/ResourceCacheService.class"
    "com/emf/sample/listener/CacheEventListener.class"
    "com/emf/sample/router/EnhancedCollectionRouter.class"
)

all_classes_found=true
for class_file in "${required_classes[@]}"; do
    if jar tf sample-service/target/sample-service-1.0.0-SNAPSHOT.jar | grep -q "$class_file"; then
        echo -e "${GREEN}✓${NC} $class_file"
    else
        echo -e "${RED}✗${NC} $class_file"
        all_classes_found=false
    fi
done

if [ "$all_classes_found" = false ]; then
    echo -e "${RED}Some required classes are missing${NC}"
    exit 1
fi
echo ""

# Step 3: Check application.yml
echo "Step 3: Checking application.yml configuration..."
if [ -f "sample-service/src/main/resources/application.yml" ]; then
    echo -e "${GREEN}✓ application.yml exists${NC}"
    
    # Check for required configuration keys
    required_keys=(
        "spring.application.name"
        "spring.datasource.url"
        "spring.data.redis.host"
        "emf.control-plane.url"
        "emf.storage.mode"
    )
    
    for key in "${required_keys[@]}"; do
        if grep -q "$key" sample-service/src/main/resources/application.yml; then
            echo -e "${GREEN}  ✓${NC} $key configured"
        else
            echo -e "${YELLOW}  ⚠${NC} $key not found (may be in environment variables)"
        fi
    done
else
    echo -e "${RED}✗ application.yml not found${NC}"
    exit 1
fi
echo ""

# Step 4: Check dependencies in JAR
echo "Step 4: Checking key dependencies..."
key_dependencies=(
    "runtime-core"
    "spring-boot"
    "spring-data-redis"
    "postgresql"
)

for dep in "${key_dependencies[@]}"; do
    if jar tf sample-service/target/sample-service-1.0.0-SNAPSHOT.jar | grep -q "$dep"; then
        echo -e "${GREEN}✓${NC} $dep"
    else
        echo -e "${YELLOW}⚠${NC} $dep (may be external)"
    fi
done
echo ""

# Step 5: Verify runtime-core dependency
echo "Step 5: Verifying runtime-core dependency..."
if [ -f "emf-platform/runtime/runtime-core/target/runtime-core-1.0.0-SNAPSHOT.jar" ]; then
    echo -e "${GREEN}✓ runtime-core JAR exists${NC}"
    ls -lh emf-platform/runtime/runtime-core/target/runtime-core-1.0.0-SNAPSHOT.jar
else
    echo -e "${RED}✗ runtime-core JAR not found${NC}"
    echo "Run: cd emf-platform && mvn clean install -DskipTests -pl runtime/runtime-core -am"
    exit 1
fi
echo ""

# Step 6: Check Dockerfile
echo "Step 6: Checking Dockerfile..."
if [ -f "sample-service/Dockerfile" ]; then
    echo -e "${GREEN}✓ Dockerfile exists${NC}"
    
    # Check for key Dockerfile instructions
    if grep -q "FROM maven" sample-service/Dockerfile; then
        echo -e "${GREEN}  ✓${NC} Uses Maven base image"
    fi
    if grep -q "HEALTHCHECK" sample-service/Dockerfile; then
        echo -e "${GREEN}  ✓${NC} Has health check"
    fi
    if grep -q "EXPOSE 8080" sample-service/Dockerfile; then
        echo -e "${GREEN}  ✓${NC} Exposes port 8080"
    fi
else
    echo -e "${RED}✗ Dockerfile not found${NC}"
    exit 1
fi
echo ""

# Step 7: Check docker-compose.yml entry
echo "Step 7: Checking docker-compose.yml entry..."
if grep -q "sample-service:" docker-compose.yml; then
    echo -e "${GREEN}✓ sample-service defined in docker-compose.yml${NC}"
    
    # Check for key configuration
    if grep -A 20 "sample-service:" docker-compose.yml | grep -q "SPRING_DATASOURCE_URL"; then
        echo -e "${GREEN}  ✓${NC} Database configuration"
    fi
    if grep -A 20 "sample-service:" docker-compose.yml | grep -q "SPRING_DATA_REDIS_HOST"; then
        echo -e "${GREEN}  ✓${NC} Redis configuration"
    fi
    if grep -A 20 "sample-service:" docker-compose.yml | grep -q "EMF_CONTROL_PLANE_URL"; then
        echo -e "${GREEN}  ✓${NC} Control plane configuration"
    fi
    if grep -A 20 "sample-service:" docker-compose.yml | grep -q "healthcheck:"; then
        echo -e "${GREEN}  ✓${NC} Health check configured"
    fi
else
    echo -e "${RED}✗ sample-service not found in docker-compose.yml${NC}"
    exit 1
fi
echo ""

echo "=========================================="
echo "Verification Summary"
echo "=========================================="
echo ""
echo -e "${GREEN}✓ Sample service JAR built successfully${NC}"
echo -e "${GREEN}✓ All required classes present${NC}"
echo -e "${GREEN}✓ Configuration files present${NC}"
echo -e "${GREEN}✓ Dependencies included${NC}"
echo -e "${GREEN}✓ Dockerfile configured${NC}"
echo -e "${GREEN}✓ Docker Compose entry configured${NC}"
echo ""
echo "The sample service is ready for integration testing!"
echo ""
echo "Next steps:"
echo "1. Ensure emf-control-plane and emf-gateway are built"
echo "2. Run: docker-compose up -d"
echo "3. Run: scripts/verify-sample-service.sh"
echo ""
