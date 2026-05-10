#!/usr/bin/env bash
# Stop hook. Runs before the worker session ends. Two checks:
#   1. /verify must pass (build + test all touched modules).
#   2. If detect-public-surface flagged any docs in .task-context/doc-required.txt,
#      those docs must also have been edited in this session.
#
# Exit 0 = let session end. Non-zero = block; Claude treats this as "needs another iteration".
#
# Worker prompt is responsible for actually invoking the fix loop on a non-zero exit.

set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}"

block() {
  echo "BLOCKED by .claude/hooks/pre-pr-gate.sh: $1" >&2
  exit 2
}

# Skip in interactive sessions (no task file = developer is working manually).
if [[ -z "${EMF_TASK_FILE:-}" ]]; then
  exit 0
fi

# 1. Doc-update gate
ctx="${CLAUDE_PROJECT_DIR:-.}/.task-context/doc-required.txt"
if [[ -f "$ctx" ]]; then
  missing=()
  while IFS= read -r doc; do
    [[ -z "$doc" ]] && continue
    # Was the doc edited in the current session? Check git diff against origin/main.
    if ! git diff --name-only origin/main -- ".claude/docs/$doc" 2>/dev/null | grep -q "$doc"; then
      missing+=("$doc")
    fi
  done < "$ctx"
  if [[ ${#missing[@]} -gt 0 ]]; then
    block "doc files required but not edited: ${missing[*]} (see .task-context/doc-required.txt)"
  fi
fi

# 2. /verify gate. Skip on a no-op session (no tracked changes).
if git diff --quiet origin/main -- 2>/dev/null && [[ -z "$(git status --porcelain)" ]]; then
  echo "[pre-pr-gate] no changes; skipping /verify"
  exit 0
fi

echo "[pre-pr-gate] running /verify..."
if ! bash .claude/commands/verify.sh 2>&1; then
  # Fall back: try the markdown command path if no .sh exists.
  if [[ -f .claude/commands/verify.md ]]; then
    echo "[pre-pr-gate] no verify.sh; reading verify.md is not executable here. Run /verify in the worker prompt instead."
    exit 0
  fi
  block "/verify failed; iterate on the failure before stopping"
fi

exit 0
