#!/usr/bin/env bash
# Print a one-screen summary of the autopilot queue + worker state.
# Safe to run on either machine. On the Mac, optionally SSHes to worker-01
# to fetch tmux session list.
#
# Usage:
#   .claude/dispatcher/status.sh            # default: pull queue, print table
#   .claude/dispatcher/status.sh --no-pull  # skip git pull (faster, may be stale)
#
# Mac launchd plist (com.cklinker.emf-status.plist) runs this every minute.

set -uo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SELF_DIR/lib/queue.sh"

EMF_QUEUE_REPO="${EMF_QUEUE_REPO:-$HOME/GitHub/emf-queue}"
WORKER_HOST="${WORKER_HOST:-worker-01}"
DO_PULL=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-pull) DO_PULL=0; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

if (( DO_PULL )); then
  git -C "$EMF_QUEUE_REPO" pull --rebase --autostash >/dev/null 2>&1 || true
fi

count() { ls -1 "$EMF_QUEUE_REPO/$1"/*.md 2>/dev/null | wc -l | tr -d ' '; }

I=$(count inbox)
R=$(count ready)
A=$(count approved)
IP=$(count in-progress)
D=$(count done)
F=$(count failed)

migration_lock="—"
[[ -f "$EMF_QUEUE_REPO/_active-migration" ]] && migration_lock="HELD"

# Workers (only meaningful from the Mac; on worker-01 itself, just `tmux ls`).
workers=""
if command -v tmux >/dev/null 2>&1 && tmux ls 2>/dev/null | grep -q '^emf-worker-'; then
  workers="$(tmux ls 2>/dev/null | awk -F: '$1 ~ /^emf-worker-/ {print $1}')"
elif command -v ssh >/dev/null 2>&1 && [[ "$WORKER_HOST" != "$(hostname)" ]]; then
  workers="$(ssh -o ConnectTimeout=3 -o BatchMode=yes "$WORKER_HOST" \
    "tmux ls 2>/dev/null | awk -F: '\$1 ~ /^emf-worker-/ {print \$1}'" 2>/dev/null || true)"
fi
worker_count="$(printf '%s\n' "$workers" | grep -c emf-worker- || true)"

# Recently merged (last 5).
recent_done="$(ls -1t "$EMF_QUEUE_REPO/done"/*.md 2>/dev/null | head -5 | xargs -n1 basename 2>/dev/null | sed 's/\.md$//')"

cat <<EOF
─── EMF autopilot status ───  $(date '+%Y-%m-%d %H:%M:%S')

Queue:
  inbox:       $I
  ready:       $R   (planner output, awaiting your review)
  approved:    $A
  in-progress: $IP
  done:        $D
  failed:      $F   $( (( F > 0 )) && echo '⚠ triage needed' )

Locks:
  migration:   $migration_lock

Workers ($worker_count active on $WORKER_HOST):
${workers:-  (none)}

Recently merged:
${recent_done:-  (none)}
EOF
