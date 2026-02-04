# Test Performance Monitoring

This directory contains scripts for monitoring and tracking integration test performance over time.

## Overview

The performance monitoring system tracks test execution times and alerts when performance degrades beyond acceptable thresholds. This helps identify performance regressions early in the development cycle.

## Features

- **Automatic Tracking**: Test execution times are automatically recorded after each test run
- **Historical Data**: Performance data is stored in JSON format for trend analysis
- **Degradation Alerts**: Alerts when test execution time increases by more than 20%
- **Performance Reports**: Generates detailed reports showing trends and slowest tests
- **CI Integration**: Automatically runs in GitHub Actions and uploads performance data as artifacts

## Usage

### Local Development

Run tests with performance monitoring:

```bash
# Run tests and monitor performance
./scripts/run-integration-tests.sh
./scripts/monitor-test-performance.sh

# View performance report
cat .test-performance/performance-report.txt
```

### CI/CD Pipeline

Performance monitoring runs automatically in GitHub Actions:

1. Tests execute via `run-integration-tests.sh --ci`
2. Performance data is collected via `monitor-test-performance.sh`
3. Performance report is added to the GitHub Actions summary
4. Performance data is uploaded as an artifact (retained for 90 days)

## Performance Data

Performance data is stored in `.test-performance/performance-history.json`:

```json
[
  {
    "timestamp": "2024-01-15T10:30:00Z",
    "commit": "abc123",
    "branch": "main",
    "totalTime": 45.3,
    "testCount": 25,
    "averageTime": 1.812,
    "tests": [
      {
        "class": "com.emf.gateway.integration.EndToEndFlowIntegrationTest",
        "time": 8.5,
        "tests": 5,
        "failures": 0,
        "errors": 0
      }
    ]
  }
]
```

## Performance Thresholds

- **Warning Threshold**: 20% increase in total execution time
- **Target Execution Time**: < 5 minutes for full test suite
- **Individual Test Target**: < 10 seconds per test

## Analyzing Performance

### View Recent Trends

```bash
# Show last 10 test runs
cat .test-performance/performance-history.json | jq '.[-10:] | .[] | {timestamp, totalTime, testCount}'
```

### Identify Slow Tests

```bash
# Show slowest tests from last run
cat .test-performance/performance-history.json | jq '.[-1].tests | sort_by(.time) | reverse | .[:5]'
```

### Calculate Average Performance

```bash
# Average execution time over last 5 runs
cat .test-performance/performance-history.json | jq '.[-5:] | map(.totalTime) | add / length'
```

## Troubleshooting Performance Issues

If performance degrades:

1. **Check the performance report** to identify slow tests
2. **Review recent changes** that might impact test execution
3. **Check Docker resource allocation** (CPU, memory)
4. **Verify infrastructure services** are healthy
5. **Look for network issues** or timeouts
6. **Check for test data accumulation** that needs cleanup

## Configuration

Edit `scripts/monitor-test-performance.sh` to adjust:

- `THRESHOLD_INCREASE`: Alert threshold percentage (default: 20%)
- Performance data retention period
- Report format and content

## Integration with CI/CD

The GitHub Actions workflow automatically:

1. Runs performance monitoring after tests
2. Uploads performance data as artifacts
3. Adds performance summary to PR comments
4. Fails the build if performance degrades significantly (optional)

To enable build failure on performance degradation, modify `.github/workflows/integration-tests.yml`:

```yaml
- name: Monitor test performance
  run: |
    chmod +x scripts/monitor-test-performance.sh
    ./scripts/monitor-test-performance.sh  # Remove '|| true' to fail on degradation
```

## Best Practices

1. **Run tests regularly** to build performance history
2. **Monitor trends** rather than individual runs (some variance is normal)
3. **Investigate immediately** when performance degrades
4. **Keep test data clean** to avoid accumulation affecting performance
5. **Review slow tests** periodically and optimize where possible

## Performance Optimization Tips

- Use parallel test execution where possible
- Minimize database operations in tests
- Use test data fixtures efficiently
- Avoid unnecessary service restarts
- Optimize Docker image builds with caching
- Use appropriate timeouts and retries

## Reporting Issues

If you notice consistent performance degradation:

1. Create an issue with the performance report
2. Include the commit range where degradation occurred
3. Attach performance data artifacts from CI
4. Describe any environmental changes
