#!/usr/bin/env bash
# Smoke test for migration-claim.sh. Sets up throwaway repos in $TMPDIR.
# Usage: bash scripts/migration-claim.test.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLAIM="$SCRIPT_DIR/migration-claim.sh"
[[ -x "$CLAIM" ]] || chmod +x "$CLAIM"

TMP="$(mktemp -d -t mig-claim-test.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/emf/kelta-worker/src/main/resources/db/migration" "$TMP/queue/in-progress"
cd "$TMP/emf"
git init -q -b main
git config user.email t@t
git config user.name t
touch kelta-worker/src/main/resources/db/migration/V100__a.sql
touch kelta-worker/src/main/resources/db/migration/V132__b.sql
git add . && git commit -q -m init
git remote add origin "$TMP/emf"
git fetch -q origin

export EMF_REPO="$TMP/emf"
export EMF_QUEUE_REPO="$TMP/queue"

assert_eq() {
  if [[ "$1" != "$2" ]]; then
    echo "FAIL: expected '$2', got '$1'" >&2; exit 1
  fi
  echo "PASS: $3 → $1"
}

# Case 1: no in-progress claims, max merged = V132 → next = V133
got="$("$CLAIM" --dry-run)"
assert_eq "$got" "V133" "no claims, merged max V132"

# Case 2: an in-progress task already claimed V134 → next = V135
cat > "$TMP/queue/in-progress/TASK-X.md" <<EOF
---
id: TASK-X
claimed_migration: V134
---
EOF
got="$("$CLAIM" --dry-run)"
assert_eq "$got" "V135" "claim V134 in-flight"

# Case 3: actual write into a task file. TASK-X already holds V134, so next is V135.
cat > "$TMP/queue/in-progress/TASK-Y.md" <<EOF
---
id: TASK-Y
needs_migration: true
---
body
EOF
got="$("$CLAIM" --task-file "$TMP/queue/in-progress/TASK-Y.md")"
assert_eq "$got" "V135" "write claim into TASK-Y"
grep -q '^claimed_migration: V135$' "$TMP/queue/in-progress/TASK-Y.md" \
  || { echo "FAIL: claim marker not written"; exit 1; }
echo "PASS: claim marker present in TASK-Y.md"

# Case 4: re-running with the same task file. TASK-Y now holds V135, so next is V136.
got="$("$CLAIM" --task-file "$TMP/queue/in-progress/TASK-Y.md")"
assert_eq "$got" "V136" "re-claim updates in place"
[[ "$(grep -c '^claimed_migration:' "$TMP/queue/in-progress/TASK-Y.md")" -eq 1 ]] \
  || { echo "FAIL: duplicate claim_migration line"; exit 1; }
echo "PASS: single claimed_migration line"

echo "ALL TESTS PASSED"
