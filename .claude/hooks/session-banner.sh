#!/usr/bin/env bash
# SessionStart hook. Prints a one-screen summary of the queue + the current
# task brief if this is a worker session.
#
# Output goes to stderr (Claude shows it as session context) and is also
# echoed to stdout so the launching tmux pane shows it.

set -uo pipefail

emit() { printf '%s\n' "$1" >&2; }

QUEUE="${EMF_QUEUE_REPO:-$HOME/GitHub/emf-queue}"

emit "─── EMF autopilot session ───"
emit "host:    $(hostname)  user: $(whoami)"
emit "cwd:     $(pwd)"
emit "branch:  $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '(no git)')"

if [[ -d "$QUEUE" ]]; then
  count() { ls -1 "$QUEUE/$1"/*.md 2>/dev/null | wc -l | tr -d ' '; }
  emit "queue:   inbox=$(count inbox)  ready=$(count ready)  approved=$(count approved)  in-progress=$(count in-progress)  failed=$(count failed)"
fi

if [[ -n "${EMF_TASK_FILE:-}" && -f "$EMF_TASK_FILE" ]]; then
  emit ""
  emit "─── current task ───"
  emit "file: $EMF_TASK_FILE"
  emit ""
  # Print the first 30 lines of the task file (frontmatter + start of brief).
  head -30 "$EMF_TASK_FILE" | sed 's/^/  /'
fi

exit 0
