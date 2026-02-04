#!/bin/bash

# Kafka Events Migration Script
# This script helps migrate from service-specific event classes to shared runtime-events

set -e

echo "=========================================="
echo "Kafka Events Migration Script"
echo "=========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Check if we're in the right directory
if [ ! -d "emf-platform" ] || [ ! -d "emf-control-plane" ] || [ ! -d "emf-gateway" ]; then
    print_error "This script must be run from the workspace root directory"
    exit 1
fi

echo "Step 1: Building runtime-events module..."
cd emf-platform/runtime/runtime-events
if mvn clean install -DskipTests > /dev/null 2>&1; then
    print_status "runtime-events built and installed successfully"
else
    print_error "Failed to build runtime-events"
    exit 1
fi
cd ../../..

echo ""
echo "Step 2: Checking for files that need import updates..."

# Find files in control-plane that need updates
echo ""
echo "Control Plane files with old imports:"
grep -r "com.emf.controlplane.event" emf-control-plane/app/src --include="*.java" -l 2>/dev/null | while read file; do
    echo "  - $file"
done

# Find files in gateway that need updates
echo ""
echo "Gateway files with old imports:"
grep -r "com.emf.gateway.event" emf-gateway/src --include="*.java" -l 2>/dev/null | while read file; do
    echo "  - $file"
done

echo ""
echo "Step 3: Listing old event classes to be deleted..."
echo ""
echo "Control Plane event classes:"
ls -1 emf-control-plane/app/src/main/java/com/emf/controlplane/event/*.java 2>/dev/null | grep -E "(ConfigEvent|ChangeType|Payload)" || echo "  (none found or already deleted)"

echo ""
echo "Gateway event classes:"
ls -1 emf-gateway/src/main/java/com/emf/gateway/event/*.java 2>/dev/null | grep -E "(ConfigEvent|ChangeType|Payload)" || echo "  (none found or already deleted)"

echo ""
echo "=========================================="
echo "Migration Status"
echo "=========================================="
echo ""
print_status "runtime-events module is built and ready"
print_warning "Manual steps required:"
echo "  1. Update imports in control-plane files (see list above)"
echo "  2. Update imports in gateway files (see list above)"
echo "  3. Update ConfigEventPublisher to use EventFactory"
echo "  4. Simplify KafkaConfig in gateway (remove type mapping)"
echo "  5. Delete old event classes after testing"
echo ""
echo "See KAFKA_EVENTS_MIGRATION_TODO.md for detailed instructions"
echo ""

# Offer to run tests
read -p "Would you like to attempt building control-plane now? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Building control-plane..."
    cd emf-control-plane/app
    if mvn clean compile -DskipTests; then
        print_status "Control plane compiled successfully"
    else
        print_error "Control plane compilation failed - imports need to be updated"
    fi
    cd ../..
fi

echo ""
read -p "Would you like to attempt building gateway now? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Building gateway..."
    cd emf-gateway
    if mvn clean compile -DskipTests; then
        print_status "Gateway compiled successfully"
    else
        print_error "Gateway compilation failed - imports need to be updated"
    fi
    cd ..
fi

echo ""
echo "=========================================="
echo "Next Steps"
echo "=========================================="
echo ""
echo "1. Review KAFKA_EVENTS_MIGRATION_TODO.md"
echo "2. Update imports in the files listed above"
echo "3. Run: mvn clean package in both services"
echo "4. Test with: docker-compose up -d --build"
echo "5. Monitor logs for Kafka errors"
echo ""
