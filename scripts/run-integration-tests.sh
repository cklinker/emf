#!/bin/bash
# Main script to run integration tests
# This script:
# 1. Starts Docker environment
# 2. Waits for all services to be healthy
# 3. Runs integration tests
# 4. Generates test report
# 5. Cleans up Docker environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CLEANUP=${CLEANUP:-true}
VERBOSE=${VERBOSE:-false}
CATEGORY=${CATEGORY:-all}
WATCH=${WATCH:-false}
CI_MODE=${CI_MODE:-false}
GENERATE_HTML=${GENERATE_HTML:-false}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Test categories
VALID_CATEGORIES=(
    "all"
    "infrastructure"
    "authentication"
    "authorization"
    "crud"
    "relationships"
    "includes"
    "cache"
    "events"
    "errors"
    "e2e"
)

# Function to print usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Run EMF platform integration tests

OPTIONS:
    -c, --category CATEGORY    Run specific test category (default: all)
                              Valid categories: ${VALID_CATEGORIES[*]}
    -w, --watch               Run tests in watch mode (re-run on file changes)
    --ci                      Run in CI mode (generate reports, collect logs)
    --html                    Generate HTML test reports after test execution
    --no-cleanup              Don't clean up Docker environment after tests
    -v, --verbose             Enable verbose output
    -h, --help                Show this help message

EXAMPLES:
    # Run all tests
    $0

    # Run only authentication tests
    $0 --category authentication

    # Run tests in watch mode for development
    $0 --watch

    # Run in CI mode with reports
    $0 --ci

    # Run with HTML report generation
    $0 --html

    # Run specific category without cleanup
    $0 --category crud --no-cleanup

EOF
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--category)
            CATEGORY="$2"
            shift 2
            ;;
        -w|--watch)
            WATCH=true
            shift
            ;;
        --ci)
            CI_MODE=true
            shift
            ;;
        --html)
            GENERATE_HTML=true
            shift
            ;;
        --no-cleanup)
            CLEANUP=false
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo -e "${RED}Error: Unknown option $1${NC}"
            usage
            ;;
    esac
done

# Validate category
if [[ ! " ${VALID_CATEGORIES[@]} " =~ " ${CATEGORY} " ]]; then
    echo -e "${RED}Error: Invalid category '$CATEGORY'${NC}"
    echo "Valid categories: ${VALID_CATEGORIES[*]}"
    exit 1
fi

# Function to log messages
log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
    fi
}

# Function to handle cleanup
cleanup() {
    if [ "$CLEANUP" = true ]; then
        log "Cleaning up Docker environment..."
        cd "$ROOT_DIR"
        docker-compose down -v > /dev/null 2>&1 || true
        log "${GREEN}✓${NC} Cleanup complete"
    else
        log "${YELLOW}Skipping cleanup (--no-cleanup flag set)${NC}"
    fi
}

# Function to collect logs on failure
collect_logs() {
    if [ "$CI_MODE" = true ]; then
        log "Collecting service logs..."
        local log_dir="$ROOT_DIR/test-logs"
        mkdir -p "$log_dir"
        
        docker-compose logs --no-color postgres > "$log_dir/postgres.log" 2>&1 || true
        docker-compose logs --no-color redis > "$log_dir/redis.log" 2>&1 || true
        docker-compose logs --no-color kafka > "$log_dir/kafka.log" 2>&1 || true
        docker-compose logs --no-color keycloak > "$log_dir/keycloak.log" 2>&1 || true
        docker-compose logs --no-color emf-control-plane > "$log_dir/control-plane.log" 2>&1 || true
        docker-compose logs --no-color emf-gateway > "$log_dir/gateway.log" 2>&1 || true
        docker-compose logs --no-color sample-service > "$log_dir/sample-service.log" 2>&1 || true
        
        log "${GREEN}✓${NC} Logs collected in $log_dir"
    fi
}

# Set up trap for cleanup
trap 'collect_logs; cleanup' EXIT INT TERM

# Function to get Maven test filter for category
get_test_filter() {
    local category=$1
    
    case $category in
        all)
            echo ""
            ;;
        infrastructure)
            echo "-Dtest=HealthCheckIntegrationTest,ControlPlaneBootstrapIntegrationTest,RedisCacheIntegrationTest,KafkaConfigurationUpdateIntegrationTest"
            ;;
        authentication)
            echo "-Dtest=AuthenticationIntegrationTest"
            ;;
        authorization)
            echo "-Dtest=AuthorizationIntegrationTest"
            ;;
        crud)
            echo "-Dtest=CollectionCrudIntegrationTest"
            ;;
        relationships)
            echo "-Dtest=RelatedCollectionsIntegrationTest"
            ;;
        includes)
            echo "-Dtest=IncludeParameterIntegrationTest"
            ;;
        cache)
            echo "-Dtest=CacheIntegrationTest"
            ;;
        events)
            echo "-Dtest=ConfigurationUpdateIntegrationTest"
            ;;
        errors)
            echo "-Dtest=ErrorHandlingIntegrationTest"
            ;;
        e2e)
            echo "-Dtest=EndToEndFlowIntegrationTest,EndToEndRequestFlowIntegrationTest"
            ;;
        *)
            echo ""
            ;;
    esac
}

# Function to run tests
run_tests() {
    log "Running integration tests (category: $CATEGORY)..."
    
    cd "$ROOT_DIR/emf-gateway"
    
    local test_filter=$(get_test_filter "$CATEGORY")
    local maven_args="test -Pintegration-test"
    
    if [ -n "$test_filter" ]; then
        maven_args="$maven_args $test_filter"
    fi
    
    if [ "$CI_MODE" = true ]; then
        maven_args="$maven_args -Dsurefire.useFile=true"
    fi
    
    if [ "$VERBOSE" = true ]; then
        maven_args="$maven_args -X"
    fi
    
    log_verbose "Maven command: mvn $maven_args"
    
    if mvn $maven_args; then
        log "${GREEN}✓${NC} Tests passed"
        return 0
    else
        log "${RED}✗${NC} Tests failed"
        return 1
    fi
}

# Function to generate test report
generate_report() {
    if [ "$CI_MODE" = true ]; then
        log "Generating test report..."
        
        cd "$ROOT_DIR/emf-gateway"
        
        # Maven Surefire generates reports automatically
        local report_dir="target/surefire-reports"
        
        if [ -d "$report_dir" ]; then
            log "${GREEN}✓${NC} Test reports available in emf-gateway/$report_dir"
            
            # Copy reports to root for CI artifacts
            mkdir -p "$ROOT_DIR/test-reports"
            cp -r "$report_dir"/* "$ROOT_DIR/test-reports/" 2>/dev/null || true
            
            # Generate summary
            local total=$(find "$report_dir" -name "TEST-*.xml" | wc -l)
            local failures=$(grep -r "failures=" "$report_dir"/TEST-*.xml 2>/dev/null | grep -o 'failures="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}')
            local errors=$(grep -r "errors=" "$report_dir"/TEST-*.xml 2>/dev/null | grep -o 'errors="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}')
            
            echo ""
            echo "=========================================="
            echo "Test Summary"
            echo "=========================================="
            echo "Total test classes: ${total:-0}"
            echo "Failures: ${failures:-0}"
            echo "Errors: ${errors:-0}"
            echo ""
        else
            log "${YELLOW}⚠${NC} No test reports found"
        fi
    fi
}

# Main execution
echo "=========================================="
echo "EMF Integration Test Runner"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  Category: $CATEGORY"
echo "  Watch mode: $WATCH"
echo "  CI mode: $CI_MODE"
echo "  HTML reports: $GENERATE_HTML"
echo "  Cleanup: $CLEANUP"
echo "  Verbose: $VERBOSE"
echo ""

# Step 1: Start Docker environment
log "Step 1: Starting Docker environment..."
cd "$ROOT_DIR"

if docker-compose ps | grep -q "Up"; then
    log "${YELLOW}Docker environment already running${NC}"
else
    log_verbose "Running: docker-compose up -d"
    docker-compose up -d
    log "${GREEN}✓${NC} Docker environment started"
fi
echo ""

# Step 2: Wait for services to be healthy
log "Step 2: Waiting for services to be healthy..."
if [ "$VERBOSE" = true ]; then
    VERBOSE=true "$SCRIPT_DIR/wait-for-services.sh"
else
    "$SCRIPT_DIR/wait-for-services.sh"
fi
echo ""

# Step 3: Run tests
log "Step 3: Running integration tests..."
echo ""

if [ "$WATCH" = true ]; then
    log "${YELLOW}Watch mode enabled - tests will re-run on file changes${NC}"
    log "Press Ctrl+C to stop"
    echo ""
    
    # Watch mode: use Maven's continuous testing
    cd "$ROOT_DIR/emf-gateway"
    local test_filter=$(get_test_filter "$CATEGORY")
    mvn test -Pintegration-test $test_filter -Dsurefire.rerunFailingTestsCount=0 || true
else
    # Single run
    if run_tests; then
        TEST_EXIT_CODE=0
    else
        TEST_EXIT_CODE=1
    fi
    echo ""
    
    # Step 4: Generate test report
    if [ "$CI_MODE" = true ] || [ "$GENERATE_HTML" = true ]; then
        log "Step 4: Generating test report..."
        generate_report
        
        # Generate HTML report if requested
        if [ "$GENERATE_HTML" = true ]; then
            log "Generating HTML test report..."
            "$SCRIPT_DIR/generate-test-report.sh" || true
        fi
        echo ""
    fi
    
    # Final summary
    echo "=========================================="
    if [ $TEST_EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}✓ Integration tests completed successfully${NC}"
    else
        echo -e "${RED}✗ Integration tests failed${NC}"
    fi
    echo "=========================================="
    echo ""
    
    exit $TEST_EXIT_CODE
fi
