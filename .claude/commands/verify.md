Build and verify all modules before creating a PR. All steps must pass with zero errors.

Run these steps in order, stopping on any failure:

## Step 1: Build runtime modules (dependency for gateway and worker)
```bash
mvn clean install -DskipTests -f kelta-platform/pom.xml -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema -am -B
```

## Step 2: Run gateway tests
```bash
mvn verify -f kelta-gateway/pom.xml -B
```

## Step 3: Run worker tests
```bash
mvn verify -f kelta-worker/pom.xml -B
```

## Step 4: Run kelta-web checks (always required — CI runs these on every PR)
```bash
cd kelta-web && npm install && npm run lint && npm run typecheck && npm run format:check && npm run test:coverage
```

## Step 5: Run kelta-ui checks (only if kelta-ui/ files were changed)
Check if any files in `kelta-ui/` were modified using `git diff --name-only main`. If yes:
```bash
cd kelta-ui/app && npm install && npm run lint && npm run format:check && npm run test:run
```

## Final Report
After all steps, print a summary checklist:
- [ ] Runtime modules built
- [ ] Gateway tests passed
- [ ] Worker tests passed
- [ ] kelta-web lint/typecheck/format/tests passed
- [ ] kelta-ui lint/format/tests passed (if applicable)

If any step failed, report the specific failure and stop.
