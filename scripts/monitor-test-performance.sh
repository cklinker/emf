#!/bin/bash
# Script to monitor and track test execution performance
# This script tracks test execution times and alerts on performance degradation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PERF_DIR="$ROOT_DIR/.test-performance"
PERF_FILE="$PERF_DIR/performance-history.json"
THRESHOLD_INCREASE=20  # Alert if test time increases by more than 20%

log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

# Create performance directory if it doesn't exist
mkdir -p "$PERF_DIR"

# Initialize performance file if it doesn't exist
if [ ! -f "$PERF_FILE" ]; then
    echo "[]" > "$PERF_FILE"
fi

# Function to extract test execution times from surefire reports
extract_test_times() {
    local report_dir="$ROOT_DIR/emf-gateway/target/surefire-reports"
    
    if [ ! -d "$report_dir" ]; then
        echo "Error: Test reports not found at $report_dir"
        return 1
    fi
    
    # Extract test times from XML reports
    local total_time=0
    local test_count=0
    
    # Create temporary file for test details
    local temp_file=$(mktemp)
    
    for xml_file in "$report_dir"/TEST-*.xml; do
        if [ -f "$xml_file" ]; then
            # Extract test class name and time
            local class_name=$(grep -o 'name="[^"]*"' "$xml_file" | head -1 | sed 's/name="//;s/"//')
            local time=$(grep -o 'time="[^"]*"' "$xml_file" | head -1 | sed 's/time="//;s/"//')
            local tests=$(grep -o 'tests="[^"]*"' "$xml_file" | head -1 | sed 's/tests="//;s/"//')
            local failures=$(grep -o 'failures="[^"]*"' "$xml_file" | head -1 | sed 's/failures="//;s/"//')
            local errors=$(grep -o 'errors="[^"]*"' "$xml_file" | head -1 | sed 's/errors="//;s/"//')
            
            if [ -n "$time" ]; then
                total_time=$(echo "$total_time + $time" | bc)
                test_count=$((test_count + ${tests:-0}))
                
                # Store test details
                echo "$class_name|$time|$tests|$failures|$errors" >> "$temp_file"
            fi
        fi
    done
    
    echo "$total_time|$test_count|$temp_file"
}

# Function to save performance data
save_performance_data() {
    local total_time=$1
    local test_count=$2
    local temp_file=$3
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local git_commit=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
    local git_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
    
    # Create JSON entry
    local entry=$(cat <<EOF
{
  "timestamp": "$timestamp",
  "commit": "$git_commit",
  "branch": "$git_branch",
  "totalTime": $total_time,
  "testCount": $test_count,
  "averageTime": $(echo "scale=3; $total_time / $test_count" | bc),
  "tests": [
EOF
)
    
    # Add individual test details
    local first=true
    while IFS='|' read -r class_name time tests failures errors; do
        if [ "$first" = true ]; then
            first=false
        else
            entry="$entry,"
        fi
        entry="$entry
    {
      \"class\": \"$class_name\",
      \"time\": $time,
      \"tests\": $tests,
      \"failures\": ${failures:-0},
      \"errors\": ${errors:-0}
    }"
    done < "$temp_file"
    
    entry="$entry
  ]
}"
    
    # Append to performance history
    local current_data=$(cat "$PERF_FILE")
    if [ "$current_data" = "[]" ]; then
        echo "[$entry]" > "$PERF_FILE"
    else
        # Remove closing bracket, add comma and new entry, add closing bracket
        echo "$current_data" | sed '$ s/]$/,/' > "$PERF_FILE.tmp"
        echo "$entry" >> "$PERF_FILE.tmp"
        echo "]" >> "$PERF_FILE.tmp"
        mv "$PERF_FILE.tmp" "$PERF_FILE"
    fi
    
    # Clean up temp file
    rm -f "$temp_file"
}

# Function to check for performance degradation
check_performance_degradation() {
    local current_time=$1
    
    # Get previous run time (second to last entry)
    local previous_time=$(cat "$PERF_FILE" | grep -o '"totalTime": [0-9.]*' | tail -2 | head -1 | grep -o '[0-9.]*')
    
    if [ -z "$previous_time" ] || [ "$previous_time" = "0" ]; then
        log "No previous performance data available for comparison"
        return 0
    fi
    
    # Calculate percentage increase
    local increase=$(echo "scale=2; (($current_time - $previous_time) / $previous_time) * 100" | bc)
    local increase_int=$(echo "$increase" | cut -d. -f1)
    
    log "Performance comparison:"
    echo "  Previous run: ${previous_time}s"
    echo "  Current run: ${current_time}s"
    echo "  Change: ${increase}%"
    echo ""
    
    # Check if increase exceeds threshold
    if [ "${increase_int:-0}" -gt "$THRESHOLD_INCREASE" ]; then
        echo -e "${RED}⚠ WARNING: Test execution time increased by ${increase}%${NC}"
        echo -e "${RED}This exceeds the threshold of ${THRESHOLD_INCREASE}%${NC}"
        echo ""
        
        # Show slowest tests
        log "Slowest tests in current run:"
        cat "$PERF_FILE" | grep -A 1000 '"tests": \[' | tail -n +2 | grep '"class"' | \
            sed 's/.*"class": "\([^"]*\)".*"time": \([^,]*\).*/\2 \1/' | \
            sort -rn | head -5 | while read time class; do
            echo "  ${time}s - $class"
        done
        echo ""
        
        return 1
    else
        log "${GREEN}✓${NC} Performance is within acceptable range"
        return 0
    fi
}

# Function to generate performance report
generate_performance_report() {
    log "Generating performance report..."
    
    local report_file="$PERF_DIR/performance-report.txt"
    
    cat > "$report_file" <<EOF
========================================
Test Performance Report
========================================
Generated: $(date)

Recent Test Runs (last 10):
EOF
    
    # Extract last 10 runs
    cat "$PERF_FILE" | grep -o '"timestamp": "[^"]*".*"totalTime": [0-9.]*.*"testCount": [0-9]*' | \
        tail -10 | while read line; do
        local timestamp=$(echo "$line" | grep -o '"timestamp": "[^"]*"' | sed 's/"timestamp": "//;s/"//')
        local total_time=$(echo "$line" | grep -o '"totalTime": [0-9.]*' | grep -o '[0-9.]*')
        local test_count=$(echo "$line" | grep -o '"testCount": [0-9]*' | grep -o '[0-9]*')
        local avg_time=$(echo "scale=3; $total_time / $test_count" | bc)
        
        echo "  $timestamp - Total: ${total_time}s, Tests: $test_count, Avg: ${avg_time}s" >> "$report_file"
    done
    
    cat >> "$report_file" <<EOF

Performance Trends:
EOF
    
    # Calculate average of last 5 runs
    local avg_last_5=$(cat "$PERF_FILE" | grep -o '"totalTime": [0-9.]*' | tail -5 | \
        awk '{sum+=$2; count++} END {if(count>0) print sum/count; else print 0}')
    
    # Calculate average of previous 5 runs
    local avg_prev_5=$(cat "$PERF_FILE" | grep -o '"totalTime": [0-9.]*' | tail -10 | head -5 | \
        awk '{sum+=$2; count++} END {if(count>0) print sum/count; else print 0}')
    
    echo "  Average of last 5 runs: ${avg_last_5}s" >> "$report_file"
    echo "  Average of previous 5 runs: ${avg_prev_5}s" >> "$report_file"
    
    if [ "$(echo "$avg_last_5 > 0" | bc)" -eq 1 ] && [ "$(echo "$avg_prev_5 > 0" | bc)" -eq 1 ]; then
        local trend=$(echo "scale=2; (($avg_last_5 - $avg_prev_5) / $avg_prev_5) * 100" | bc)
        echo "  Trend: ${trend}%" >> "$report_file"
    fi
    
    cat "$report_file"
    
    log "${GREEN}✓${NC} Performance report saved to $report_file"
}

# Main execution
echo "=========================================="
echo "Test Performance Monitor"
echo "=========================================="
echo ""

log "Extracting test execution times..."
result=$(extract_test_times)

if [ $? -eq 0 ]; then
    total_time=$(echo "$result" | cut -d'|' -f1)
    test_count=$(echo "$result" | cut -d'|' -f2)
    temp_file=$(echo "$result" | cut -d'|' -f3)
    
    log "Test execution summary:"
    echo "  Total time: ${total_time}s"
    echo "  Test count: $test_count"
    echo "  Average time per test: $(echo "scale=3; $total_time / $test_count" | bc)s"
    echo ""
    
    log "Saving performance data..."
    save_performance_data "$total_time" "$test_count" "$temp_file"
    log "${GREEN}✓${NC} Performance data saved"
    echo ""
    
    log "Checking for performance degradation..."
    if check_performance_degradation "$total_time"; then
        PERF_STATUS=0
    else
        PERF_STATUS=1
    fi
    echo ""
    
    generate_performance_report
    echo ""
    
    echo "=========================================="
    if [ $PERF_STATUS -eq 0 ]; then
        echo -e "${GREEN}✓ Performance monitoring complete${NC}"
    else
        echo -e "${YELLOW}⚠ Performance degradation detected${NC}"
    fi
    echo "=========================================="
    
    exit $PERF_STATUS
else
    echo -e "${RED}Error: Failed to extract test times${NC}"
    exit 1
fi
