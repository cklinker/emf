#!/bin/bash
# Wait for all services to be healthy before running tests
# This script checks health endpoints with retry logic and timeout

# Note: We don't use 'set -e' here because we expect health checks to fail
# during the waiting period, and we want to continue retrying

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MAX_WAIT_TIME=${MAX_WAIT_TIME:-300}  # 5 minutes default
CHECK_INTERVAL=${CHECK_INTERVAL:-5}  # 5 seconds between checks
VERBOSE=${VERBOSE:-false}

# Service URLs
POSTGRES_HOST=${POSTGRES_HOST:-localhost}
POSTGRES_PORT=${POSTGRES_PORT:-5432}
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}
KAFKA_HOST=${KAFKA_HOST:-localhost}
KAFKA_PORT=${KAFKA_PORT:-9094}
KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8180}
CONTROL_PLANE_URL=${CONTROL_PLANE_URL:-http://localhost:8081}
GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
SAMPLE_SERVICE_URL=${SAMPLE_SERVICE_URL:-http://localhost:8082}

# Track start time
START_TIME=$(date +%s)

# Function to log messages
log() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
    fi
}

# Function to check elapsed time
check_timeout() {
    local current_time=$(date +%s)
    local elapsed=$((current_time - START_TIME))
    
    if [ $elapsed -ge $MAX_WAIT_TIME ]; then
        echo -e "${RED}✗ Timeout: Services did not become healthy within ${MAX_WAIT_TIME} seconds${NC}"
        echo ""
        echo "Failed services:"
        check_all_services_status
        exit 1
    fi
}

# Function to check if PostgreSQL is ready
check_postgres() {
    if command -v pg_isready > /dev/null 2>&1; then
        pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" > /dev/null 2>&1
    else
        # Fallback: try to connect via docker
        docker exec emf-postgres pg_isready -U emf > /dev/null 2>&1
    fi
}

# Function to check if Redis is ready
check_redis() {
    if command -v redis-cli > /dev/null 2>&1; then
        redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping > /dev/null 2>&1
    else
        # Fallback: try via docker
        docker exec emf-redis redis-cli ping > /dev/null 2>&1
    fi
}

# Function to check if Kafka is ready
check_kafka() {
    # Check if Kafka port is open
    if command -v nc > /dev/null 2>&1; then
        nc -z "$KAFKA_HOST" "$KAFKA_PORT" > /dev/null 2>&1
    else
        # Fallback: check docker container status
        docker ps --filter "name=emf-kafka" --filter "status=running" | grep -q "emf-kafka"
    fi
}

# Function to check HTTP health endpoint
check_http_health() {
    local url=$1
    local response=$(curl -sf "$url/actuator/health" 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        # Check if response contains "UP" status
        if echo "$response" | grep -q '"status":"UP"'; then
            return 0
        fi
    fi
    return 1
}

# Function to check Keycloak health
check_keycloak() {
    # Keycloak doesn't have /actuator/health, check realm endpoint
    curl -sf "$KEYCLOAK_URL/realms/emf" > /dev/null 2>&1
}

# Function to check all services and return status
check_all_services_status() {
    local all_healthy=true
    
    if check_postgres; then
        echo -e "  ${GREEN}✓${NC} PostgreSQL"
    else
        echo -e "  ${RED}✗${NC} PostgreSQL"
        all_healthy=false
    fi
    
    if check_redis; then
        echo -e "  ${GREEN}✓${NC} Redis"
    else
        echo -e "  ${RED}✗${NC} Redis"
        all_healthy=false
    fi
    
    if check_kafka; then
        echo -e "  ${GREEN}✓${NC} Kafka"
    else
        echo -e "  ${RED}✗${NC} Kafka"
        all_healthy=false
    fi
    
    if check_keycloak; then
        echo -e "  ${GREEN}✓${NC} Keycloak"
    else
        echo -e "  ${RED}✗${NC} Keycloak"
        all_healthy=false
    fi
    
    if check_http_health "$CONTROL_PLANE_URL"; then
        echo -e "  ${GREEN}✓${NC} Control Plane"
    else
        echo -e "  ${RED}✗${NC} Control Plane"
        all_healthy=false
    fi
    
    if check_http_health "$GATEWAY_URL"; then
        echo -e "  ${GREEN}✓${NC} Gateway"
    else
        echo -e "  ${RED}✗${NC} Gateway"
        all_healthy=false
    fi
    
    if check_http_health "$SAMPLE_SERVICE_URL"; then
        echo -e "  ${GREEN}✓${NC} Sample Service"
    else
        echo -e "  ${RED}✗${NC} Sample Service"
        all_healthy=false
    fi
    
    if [ "$all_healthy" = true ]; then
        return 0
    else
        return 1
    fi
}

# Main waiting loop
echo "=========================================="
echo "Waiting for Services to be Healthy"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  Max wait time: ${MAX_WAIT_TIME}s"
echo "  Check interval: ${CHECK_INTERVAL}s"
echo ""

# Give services a moment to start before first check
log "Waiting for services to initialize..."
sleep 5

# Initial status check
echo "Initial service status:"
check_all_services_status
echo ""

# Wait for all services
while true; do
    check_timeout
    
    log "Checking service health..."
    
    if check_all_services_status > /dev/null 2>&1; then
        echo -e "${GREEN}✓ All services are healthy!${NC}"
        echo ""
        
        # Final status display
        echo "Service status:"
        check_all_services_status
        echo ""
        
        # Calculate total wait time
        end_time=$(date +%s)
        total_wait=$((end_time - START_TIME))
        echo "Total wait time: ${total_wait}s"
        echo ""
        
        exit 0
    fi
    
    log "Some services are not ready yet, waiting ${CHECK_INTERVAL}s..."
    sleep $CHECK_INTERVAL
done
