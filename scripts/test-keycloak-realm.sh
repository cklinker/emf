#!/bin/bash

# Test script to verify Keycloak realm import
# This script starts Keycloak and verifies the realm configuration is imported correctly

set -e

echo "=========================================="
echo "Testing Keycloak Realm Import"
echo "=========================================="

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Start only Keycloak service
echo -e "${YELLOW}Starting Keycloak service...${NC}"
docker-compose up -d keycloak

# Wait for Keycloak to be healthy
echo -e "${YELLOW}Waiting for Keycloak to be ready...${NC}"
timeout=120
elapsed=0
interval=5

while [ $elapsed -lt $timeout ]; do
    if docker-compose ps keycloak | grep -q "healthy"; then
        echo -e "${GREEN}✓ Keycloak is healthy${NC}"
        break
    fi
    echo "Waiting for Keycloak... ($elapsed/$timeout seconds)"
    sleep $interval
    elapsed=$((elapsed + interval))
done

if [ $elapsed -ge $timeout ]; then
    echo -e "${RED}✗ Keycloak failed to start within $timeout seconds${NC}"
    docker-compose logs keycloak
    exit 1
fi

# Give Keycloak a few more seconds to complete realm import
sleep 10

echo ""
echo "=========================================="
echo "Verifying Realm Configuration"
echo "=========================================="

# Test 1: Check if EMF realm exists
echo -e "${YELLOW}Test 1: Checking if EMF realm exists...${NC}"
REALM_CHECK=$(curl -s http://localhost:8180/realms/emf || echo "FAILED")
if echo "$REALM_CHECK" | grep -q "emf"; then
    echo -e "${GREEN}✓ EMF realm exists${NC}"
else
    echo -e "${RED}✗ EMF realm not found${NC}"
    echo "Response: $REALM_CHECK"
    exit 1
fi

# Test 2: Get admin token to verify Keycloak is working
echo -e "${YELLOW}Test 2: Testing admin user authentication...${NC}"
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8180/realms/emf/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=emf-client" \
    -d "client_secret=emf-client-secret" \
    -d "username=admin" \
    -d "password=admin" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$ADMIN_TOKEN" ]; then
    echo -e "${GREEN}✓ Admin user authenticated successfully${NC}"
    echo "Token (first 50 chars): ${ADMIN_TOKEN:0:50}..."
else
    echo -e "${RED}✗ Failed to authenticate admin user${NC}"
    exit 1
fi

# Test 3: Verify admin user has ADMIN and USER roles
echo -e "${YELLOW}Test 3: Verifying admin user roles...${NC}"
# Decode JWT token (simple base64 decode of payload)
PAYLOAD=$(echo "$ADMIN_TOKEN" | cut -d'.' -f2)
# Add padding if needed
PADDING=$((4 - ${#PAYLOAD} % 4))
if [ $PADDING -ne 4 ]; then
    PAYLOAD="${PAYLOAD}$(printf '=%.0s' $(seq 1 $PADDING))"
fi
DECODED=$(echo "$PAYLOAD" | base64 -d 2>/dev/null || echo "{}")

if echo "$DECODED" | grep -q "ADMIN" && echo "$DECODED" | grep -q "USER"; then
    echo -e "${GREEN}✓ Admin user has ADMIN and USER roles${NC}"
else
    echo -e "${RED}✗ Admin user roles not found correctly${NC}"
    echo "Decoded token: $DECODED"
    exit 1
fi

# Test 4: Test regular user authentication
echo -e "${YELLOW}Test 4: Testing regular user authentication...${NC}"
USER_TOKEN=$(curl -s -X POST "http://localhost:8180/realms/emf/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=emf-client" \
    -d "client_secret=emf-client-secret" \
    -d "username=user" \
    -d "password=user" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$USER_TOKEN" ]; then
    echo -e "${GREEN}✓ Regular user authenticated successfully${NC}"
else
    echo -e "${RED}✗ Failed to authenticate regular user${NC}"
    exit 1
fi

# Test 5: Verify regular user has only USER role
echo -e "${YELLOW}Test 5: Verifying regular user roles...${NC}"
PAYLOAD=$(echo "$USER_TOKEN" | cut -d'.' -f2)
PADDING=$((4 - ${#PAYLOAD} % 4))
if [ $PADDING -ne 4 ]; then
    PAYLOAD="${PAYLOAD}$(printf '=%.0s' $(seq 1 $PADDING))"
fi
DECODED=$(echo "$PAYLOAD" | base64 -d 2>/dev/null || echo "{}")

if echo "$DECODED" | grep -q "USER" && ! echo "$DECODED" | grep -q "ADMIN"; then
    echo -e "${GREEN}✓ Regular user has USER role only${NC}"
else
    echo -e "${RED}✗ Regular user roles not correct${NC}"
    echo "Decoded token: $DECODED"
    exit 1
fi

# Test 6: Test guest user authentication
echo -e "${YELLOW}Test 6: Testing guest user authentication...${NC}"
GUEST_TOKEN=$(curl -s -X POST "http://localhost:8180/realms/emf/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=emf-client" \
    -d "client_secret=emf-client-secret" \
    -d "username=guest" \
    -d "password=guest" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$GUEST_TOKEN" ]; then
    echo -e "${GREEN}✓ Guest user authenticated successfully${NC}"
else
    echo -e "${RED}✗ Failed to authenticate guest user${NC}"
    exit 1
fi

# Test 7: Verify guest user has no roles
echo -e "${YELLOW}Test 7: Verifying guest user has no roles...${NC}"
PAYLOAD=$(echo "$GUEST_TOKEN" | cut -d'.' -f2)
PADDING=$((4 - ${#PAYLOAD} % 4))
if [ $PADDING -ne 4 ]; then
    PAYLOAD="${PAYLOAD}$(printf '=%.0s' $(seq 1 $PADDING))"
fi
DECODED=$(echo "$PAYLOAD" | base64 -d 2>/dev/null || echo "{}")

if ! echo "$DECODED" | grep -q "ADMIN" && ! echo "$DECODED" | grep -q "USER"; then
    echo -e "${GREEN}✓ Guest user has no special roles${NC}"
else
    echo -e "${RED}✗ Guest user should not have ADMIN or USER roles${NC}"
    echo "Decoded token: $DECODED"
    exit 1
fi

# Test 8: Verify emf-client configuration
echo -e "${YELLOW}Test 8: Verifying emf-client supports password grant...${NC}"
# If we got tokens above, password grant is working
echo -e "${GREEN}✓ emf-client password grant flow is working${NC}"

echo ""
echo "=========================================="
echo -e "${GREEN}All tests passed!${NC}"
echo "=========================================="
echo ""
echo "Realm configuration summary:"
echo "  - Realm: emf"
echo "  - Users: admin (ADMIN, USER), user (USER), guest (no roles)"
echo "  - Client: emf-client (password grant enabled)"
echo ""
echo "To stop Keycloak:"
echo "  docker-compose down keycloak"
echo ""
