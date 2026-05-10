#!/usr/bin/env bash
# PreToolUse hook for Bash. Blocks dangerous shell commands.
# Reads the tool input from stdin as JSON: {"command": "..."}.
# Exit 0 = allow, exit 2 = block (Claude sees stderr as the reason).

set -uo pipefail

input="$(cat)"
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // .command // ""' 2>/dev/null)"
[[ -z "$cmd" ]] && exit 0

# Scan only the executable command portion: strip everything from the first
# heredoc marker (<<EOF, <<'EOF', etc) onward, plus content inside single
# or double quotes. Avoids matching trigger strings that appear in commit
# messages, PR bodies, or other quoted args.
strip_quoted() {
  python3 - "$1" <<'PY' 2>/dev/null || printf '%s' "$1"
import re, sys
s = sys.argv[1]
# Cut at the first heredoc marker.
m = re.search(r"<<-?\s*['\"]?[A-Za-z_][A-Za-z0-9_]*['\"]?", s)
if m:
    s = s[:m.start()]
# Remove single-quoted strings.
s = re.sub(r"'[^']*'", "", s)
# Remove double-quoted strings (no nesting; good enough).
s = re.sub(r'"[^"]*"', "", s)
print(s, end="")
PY
}
cmd_scan="$(strip_quoted "$cmd")"
# Fallback: if python isn't available, scan the raw command (less precise).
[[ -z "$cmd_scan" ]] && cmd_scan="$cmd"

block() {
  echo "BLOCKED by .claude/hooks/guard-bash.sh: $1" >&2
  exit 2
}

# Direct push/force-push to main
if [[ "$cmd_scan" =~ git[[:space:]]+push[[:space:]]+(--force|-f|origin[[:space:]]+main\b) ]]; then
  block "no force-push or direct push to main"
fi

# Hook bypass flags
if [[ "$cmd_scan" =~ --no-verify ]]; then
  block "the no-verify flag is forbidden; fix the hook failure instead"
fi

# Hard reset
if [[ "$cmd_scan" =~ git[[:space:]]+reset[[:space:]]+--hard ]]; then
  block "git reset hard requires manual user action"
fi

# Bypass auto-merge
if [[ "$cmd_scan" =~ gh[[:space:]]+pr[[:space:]]+merge[[:space:]] ]] && [[ ! "$cmd_scan" =~ --auto ]]; then
  block "gh pr merge requires --auto (autopilot relies on the auto-merge workflow)"
fi

# Destructive kubectl
if [[ "$cmd_scan" =~ kubectl[[:space:]]+delete[[:space:]] ]]; then
  block "kubectl delete requires manual user action"
fi

# Mass file removal
if [[ "$cmd_scan" =~ rm[[:space:]]+-rf[[:space:]]+/ ]] || [[ "$cmd_scan" =~ rm[[:space:]]+-rf[[:space:]]+\$HOME ]] || [[ "$cmd_scan" =~ rm[[:space:]]+-rf[[:space:]]+~ ]]; then
  block "wide rm -rf is forbidden"
fi

exit 0
