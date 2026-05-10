#!/usr/bin/env bash
# Stop hook. For autopilot-feature tasks, ensure at least one test file was
# added or modified in the session.
#
# Disabled for: interactive sessions (no EMF_TASK_FILE), bug tasks (a fix may
# touch existing tests), chore tasks (deps bumps), doc tasks.

set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}"

# Skip in interactive sessions.
[[ -z "${EMF_TASK_FILE:-}" ]] && exit 0
[[ ! -f "$EMF_TASK_FILE" ]] && exit 0

task_type="$(grep -E '^type:' "$EMF_TASK_FILE" | head -1 | awk '{print $2}' | tr -d '"' || true)"
case "$task_type" in
  feature) ;;  # enforce
  bug|chore|doc|security) exit 0 ;;
  *) exit 0 ;;
esac

# Look for any added/modified test files since origin/main.
test_changes="$(git diff --name-only origin/main -- \
  '**/*Test.java' '**/*Tests.java' '**/*IT.java' \
  '**/*.test.ts' '**/*.test.tsx' '**/*.spec.ts' '**/*.spec.tsx' \
  'e2e-tests/**/*.spec.ts' 2>/dev/null | head -1)"

if [[ -z "$test_changes" ]]; then
  echo "BLOCKED by .claude/hooks/require-tests.sh: feature task with no test changes (per feedback_ui-and-testing.md)" >&2
  echo "Add a unit test (Java *Test.java or vitest *.test.ts) and an E2E spec (e2e-tests/*.spec.ts) before stopping." >&2
  exit 2
fi

exit 0
