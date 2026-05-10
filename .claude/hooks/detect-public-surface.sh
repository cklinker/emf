#!/usr/bin/env bash
# PostToolUse hook for Edit/Write/MultiEdit. Detects when a change touches
# public surface (REST controllers, JPA entities, system-collection schemas,
# external SDK deps, new Flyway migrations) and appends the relevant
# .claude/docs/*.md filename(s) to .task-context/doc-required.txt.
#
# pre-pr-gate.sh later refuses to finish the session unless those doc files
# were also edited in the session.
#
# Hook does NOT block on its own — it just records what's needed.

set -uo pipefail

input="$(cat)"
file="$(printf '%s' "$input" | jq -r '.tool_input.file_path // ""' 2>/dev/null)"
[[ -z "$file" || ! -f "$file" ]] && exit 0

ctx_dir="${CLAUDE_PROJECT_DIR:-.}/.task-context"
mkdir -p "$ctx_dir"
out="$ctx_dir/doc-required.txt"

mark() {
  doc="$1"; reason="$2"
  if ! grep -qx "$doc" "$out" 2>/dev/null; then
    echo "$doc" >> "$out"
  fi
  echo "[detect-public-surface] $file → $doc ($reason)" >&2
}

# REST controllers
if [[ "$file" == *.java ]] && grep -qE '@RestController|@RequestMapping|@(Get|Post|Put|Delete|Patch)Mapping' "$file" 2>/dev/null; then
  mark "architecture.md" "new/changed REST endpoint"
fi

# JPA entities
if [[ "$file" == *.java ]] && grep -qE '^[[:space:]]*@Entity\b' "$file" 2>/dev/null; then
  mark "architecture.md" "new/changed JPA entity"
fi

# Flyway migrations
case "$file" in
  *kelta-worker/src/main/resources/db/migration/V*__*.sql)
    mark "architecture.md" "new Flyway migration"
    if grep -qiE '\b(grant|revoke|policy|password|secret|token)\b' "$file"; then
      mark "concerns.md" "migration touches security-relevant tables"
    fi
    ;;
esac

# Dependency files (external SDK additions trigger integrations.md)
case "$file" in
  */pom.xml|*/package.json)
    # Heuristic: bias toward false-positive — require the doc on any dep file change.
    mark "integrations.md" "dependency file changed"
    ;;
esac

# Test pattern files (new test base classes / utils)
case "$file" in
  *src/test/java/**/Abstract*Test*.java|*src/test/java/**/*TestSupport*.java|*test-utils/*)
    mark "testing.md" "new test pattern / support class"
    ;;
esac

exit 0
