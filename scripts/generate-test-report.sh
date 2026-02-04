#!/bin/bash
# Script to generate HTML test reports from JUnit XML files
# This script generates comprehensive HTML reports from test results

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

echo "=========================================="
echo "EMF Test Report Generator"
echo "=========================================="
echo ""

# Check if test reports exist
if [ ! -d "$ROOT_DIR/emf-gateway/target/surefire-reports" ]; then
    echo -e "${YELLOW}No test reports found. Run tests first with:${NC}"
    echo "  ./scripts/run-integration-tests.sh"
    exit 1
fi

log "Generating HTML test reports..."

# Generate Maven site with test reports
cd "$ROOT_DIR/emf-gateway"
mvn surefire-report:report-only site:site -DgenerateReports=false

if [ -f "target/site/surefire-report.html" ]; then
    log "${GREEN}✓${NC} HTML test report generated"
    echo ""
    echo "Test report available at:"
    echo "  file://$ROOT_DIR/emf-gateway/target/site/surefire-report.html"
    echo ""
    
    # Generate summary
    log "Test Summary:"
    
    REPORT_DIR="target/surefire-reports"
    TOTAL_CLASSES=$(find "$REPORT_DIR" -name "TEST-*.xml" | wc -l)
    TOTAL_TESTS=$(grep -r 'tests="' "$REPORT_DIR"/TEST-*.xml 2>/dev/null | grep -o 'tests="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}')
    FAILURES=$(grep -r 'failures="' "$REPORT_DIR"/TEST-*.xml 2>/dev/null | grep -o 'failures="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}')
    ERRORS=$(grep -r 'errors="' "$REPORT_DIR"/TEST-*.xml 2>/dev/null | grep -o 'errors="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}')
    SKIPPED=$(grep -r 'skipped="' "$REPORT_DIR"/TEST-*.xml 2>/dev/null | grep -o 'skipped="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}')
    
    # Calculate execution time
    TIME=$(grep -r 'time="' "$REPORT_DIR"/TEST-*.xml 2>/dev/null | grep -o 'time="[0-9.]*"' | grep -o '[0-9.]*' | awk '{s+=$1} END {print s}')
    
    echo "  Total test classes: ${TOTAL_CLASSES:-0}"
    echo "  Total tests: ${TOTAL_TESTS:-0}"
    echo "  Passed: $((${TOTAL_TESTS:-0} - ${FAILURES:-0} - ${ERRORS:-0} - ${SKIPPED:-0}))"
    echo "  Failures: ${FAILURES:-0}"
    echo "  Errors: ${ERRORS:-0}"
    echo "  Skipped: ${SKIPPED:-0}"
    echo "  Execution time: ${TIME:-0}s"
    echo ""
    
    # Check for code coverage report
    if [ -f "target/site/jacoco/index.html" ]; then
        log "${GREEN}✓${NC} Code coverage report generated"
        echo "Coverage report available at:"
        echo "  file://$ROOT_DIR/emf-gateway/target/site/jacoco/index.html"
        echo ""
    fi
    
    # Open report in browser (optional)
    if command -v open &> /dev/null; then
        read -p "Open report in browser? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            open "target/site/surefire-report.html"
        fi
    fi
else
    echo -e "${YELLOW}⚠${NC} Failed to generate HTML report"
    exit 1
fi

echo "=========================================="
echo -e "${GREEN}✓ Report generation complete${NC}"
echo "=========================================="
