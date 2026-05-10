#!/usr/bin/env bash
# Claim the next eligible task from emf-queue/approved/. Echoes the new
# in-progress path on success (e.g. /home/craig/GitHub/emf-queue/in-progress/TASK-X.md).
# Exits non-zero with no output if nothing claimable.
#
# Used by dispatch.sh in a loop. Standalone-callable for testing.
#
# Usage:
#   .claude/dispatcher/claim.sh [--owner NAME]

set -uo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SELF_DIR/lib/log.sh"
. "$SELF_DIR/lib/queue.sh"

EMF_LOG_COMPONENT="claim"
log_init "claim"

OWNER="${HOSTNAME:-$(hostname)}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --owner) OWNER="$2"; shift 2 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

queue_pull >/dev/null 2>&1 || {
  log_error "queue_pull failed"
  exit 1
}

while IFS= read -r src; do
  [[ -z "$src" ]] && continue
  base="$(basename "$src")"
  id="${base%.md}"

  EMF_LOG_TASK_ID="$id"
  log_event task_claim_attempt task="$id" file="$src"

  dest="$(queue_claim "$src" "$OWNER")"
  rc=$?
  if (( rc == 0 )) && [[ -n "$dest" ]]; then
    log_event task_claimed task="$id" owner="$OWNER" path="$dest"
    echo "$dest"
    exit 0
  fi
  log_warn "claim failed (rc=$rc), trying next" task="$id"
done < <(queue_eligible_tasks)

log_event no_eligible_tasks
exit 1
