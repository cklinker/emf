#!/bin/bash

# EMF Development Environment Helper Script
# Usage: ./scripts/dev-env.sh [command]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  EMF Development Environment${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

# Start core services (postgres, redis, kafka, keycloak)
start() {
    print_header
    echo "Starting core services..."
    docker compose up -d postgres redis kafka keycloak
    
    echo ""
    print_status "Waiting for services to be healthy..."
    
    # Wait for postgres
    echo -n "  PostgreSQL: "
    until docker compose exec -T postgres pg_isready -U emf -d emf_control_plane > /dev/null 2>&1; do
        echo -n "."
        sleep 2
    done
    echo -e " ${GREEN}ready${NC}"
    
    # Wait for redis
    echo -n "  Redis: "
    until docker compose exec -T redis redis-cli ping > /dev/null 2>&1; do
        echo -n "."
        sleep 2
    done
    echo -e " ${GREEN}ready${NC}"
    
    # Wait for kafka
    echo -n "  Kafka: "
    sleep 5  # Give Kafka extra time to initialize
    until docker compose exec -T kafka /opt/kafka/bin/kafka-cluster.sh cluster-id --bootstrap-server localhost:9092 > /dev/null 2>&1; do
        echo -n "."
        sleep 3
    done
    echo -e " ${GREEN}ready${NC}"
    
    # Wait for keycloak
    echo -n "  Keycloak: "
    until curl -sf http://localhost:8180/health/ready > /dev/null 2>&1; do
        echo -n "."
        sleep 3
    done
    echo -e " ${GREEN}ready${NC}"
    
    echo ""
    print_status "All services are running!"
    echo ""
    show_urls
}

# Start with optional tools (kafka-ui, redis-commander, pgadmin)
start_with_tools() {
    print_header
    echo "Starting all services including dev tools..."
    docker compose --profile tools up -d
    
    echo ""
    print_warning "Dev tools are starting in background. They may take a moment to be ready."
    echo ""
    show_urls
    show_tool_urls
}

# Stop all services
stop() {
    print_header
    echo "Stopping all services..."
    docker compose --profile tools down
    print_status "All services stopped"
}

# Stop and remove volumes (clean slate)
clean() {
    print_header
    print_warning "This will remove all data volumes!"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker compose --profile tools down -v
        print_status "All services stopped and volumes removed"
    else
        echo "Cancelled"
    fi
}

# Show service status
status() {
    print_header
    echo "Service Status:"
    echo ""
    docker compose ps
}

# Show logs
logs() {
    local service=${1:-}
    if [ -z "$service" ]; then
        docker compose logs -f
    else
        docker compose logs -f "$service"
    fi
}

# Show connection URLs
show_urls() {
    echo -e "${BLUE}Service URLs:${NC}"
    echo "  PostgreSQL:  localhost:5432 (user: emf, pass: emf, db: emf_control_plane)"
    echo "  Redis:       localhost:6379"
    echo "  Kafka:       localhost:9094"
    echo "  Keycloak:    http://localhost:8180 (admin/admin)"
    echo ""
    echo -e "${BLUE}Keycloak OIDC Endpoints:${NC}"
    echo "  Issuer:      http://localhost:8180/realms/emf"
    echo "  JWKS:        http://localhost:8180/realms/emf/protocol/openid-connect/certs"
    echo "  Token:       http://localhost:8180/realms/emf/protocol/openid-connect/token"
    echo ""
    echo -e "${BLUE}Test Users (password = username):${NC}"
    echo "  admin     - Full admin access (ADMIN, DEVELOPER, VIEWER roles)"
    echo "  developer - Developer access (DEVELOPER, VIEWER roles)"
    echo "  viewer    - Read-only access (VIEWER role)"
}

show_tool_urls() {
    echo ""
    echo -e "${BLUE}Dev Tool URLs:${NC}"
    echo "  Kafka UI:        http://localhost:8090"
    echo "  Redis Commander: http://localhost:8091"
    echo "  pgAdmin:         http://localhost:8092 (admin@emf.local / admin)"
}

# Get a token for testing
get_token() {
    local user=${1:-admin}
    local pass=${2:-$user}
    
    echo "Getting token for user: $user"
    
    TOKEN=$(curl -s -X POST "http://localhost:8180/realms/emf/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "client_id=emf-ui" \
        -d "grant_type=password" \
        -d "username=$user" \
        -d "password=$pass" | jq -r '.access_token')
    
    if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
        echo ""
        echo -e "${GREEN}Access Token:${NC}"
        echo "$TOKEN"
        echo ""
        echo -e "${BLUE}Decoded payload:${NC}"
        echo "$TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq . 2>/dev/null || echo "(install jq for decoded view)"
    else
        print_error "Failed to get token. Is Keycloak running?"
    fi
}

# Create Kafka topics
create_topics() {
    print_header
    echo "Creating Kafka topics..."
    
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
        --bootstrap-server localhost:9092 \
        --topic config.collection.changed \
        --partitions 3 \
        --replication-factor 1
    
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
        --bootstrap-server localhost:9092 \
        --topic config.authz.changed \
        --partitions 3 \
        --replication-factor 1
    
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
        --bootstrap-server localhost:9092 \
        --topic config.ui.changed \
        --partitions 3 \
        --replication-factor 1
    
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
        --bootstrap-server localhost:9092 \
        --topic config.oidc.changed \
        --partitions 3 \
        --replication-factor 1
    
    print_status "Topics created"
    
    echo ""
    echo "Listing topics:"
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
}

# Show help
help() {
    print_header
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  start         Start core services (postgres, redis, kafka, keycloak)"
    echo "  start-tools   Start all services including dev tools (kafka-ui, pgadmin, etc)"
    echo "  stop          Stop all services"
    echo "  clean         Stop services and remove all data volumes"
    echo "  status        Show service status"
    echo "  logs [svc]    Show logs (optionally for specific service)"
    echo "  urls          Show connection URLs and credentials"
    echo "  token [user]  Get an access token (default: admin)"
    echo "  topics        Create Kafka topics"
    echo "  help          Show this help message"
    echo ""
}

# Main command handler
case "${1:-help}" in
    start)
        start
        ;;
    start-tools)
        start_with_tools
        ;;
    stop)
        stop
        ;;
    clean)
        clean
        ;;
    status)
        status
        ;;
    logs)
        logs "$2"
        ;;
    urls)
        show_urls
        show_tool_urls
        ;;
    token)
        get_token "$2" "$3"
        ;;
    topics)
        create_topics
        ;;
    help|--help|-h)
        help
        ;;
    *)
        print_error "Unknown command: $1"
        help
        exit 1
        ;;
esac
