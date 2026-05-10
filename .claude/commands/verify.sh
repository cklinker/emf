#!/usr/bin/env bash
# Script-form of verify.md. Used by .claude/hooks/pre-pr-gate.sh in autopilot
# worker sessions. CI runs the same steps via .github/workflows/ci.yml.
#
# Stops on first failure with a clear message. Skips kelta-ui if no files
# under kelta-ui/ have changed since origin/main.

set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}"
[[ -d kelta-platform ]] || { echo "Not in EMF repo root (no kelta-platform/)"; exit 2; }

step() { echo; echo "── $1 ──"; }
fail() { echo "FAILED: $1" >&2; exit 1; }

step "1/5 build runtime modules"
mvn clean install -DskipTests \
  -f kelta-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-messaging-nats,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B \
  || fail "runtime build"

step "2/5 gateway tests"
mvn verify -f kelta-gateway/pom.xml -B || fail "gateway tests"

step "3/5 worker tests"
mvn verify -f kelta-worker/pom.xml -B || fail "worker tests"

step "4/5 kelta-web checks"
( cd kelta-web && npm install && npm run lint && npm run typecheck && npm run format:check && npm run test:coverage ) \
  || fail "kelta-web"

# Step 5 only if kelta-ui touched.
if git diff --name-only origin/main 2>/dev/null | grep -q '^kelta-ui/'; then
  step "5/5 kelta-ui checks (changes detected)"
  ( cd kelta-ui/app && npm install && npm run lint && npm run format:check && npm run test:run ) \
    || fail "kelta-ui"
else
  step "5/5 kelta-ui (skipped — no changes since origin/main)"
fi

echo
echo "/verify: ALL GREEN"
