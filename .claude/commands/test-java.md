Build runtime modules and run Java tests for gateway, worker, or both.

Usage: `/test-java` (both), `/test-java gateway`, `/test-java worker`

Argument: $ARGUMENTS (optional: "gateway", "worker", or empty for both)

## Step 1: Build runtime modules (always required as dependency)
```bash
mvn clean install -DskipTests -f kelta-platform/pom.xml -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema -am -B
```

## Step 2: Run tests based on argument

If argument is "gateway" or empty:
```bash
mvn verify -f kelta-gateway/pom.xml -B
```

If argument is "worker" or empty:
```bash
mvn verify -f kelta-worker/pom.xml -B
```

Report pass/fail for each module tested.
