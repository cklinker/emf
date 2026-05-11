#!/usr/bin/env bash
# Dispatcher loop. Runs as a systemd service on worker-01.
#
# Each tick:
#   1. Pull emf-queue + emf to latest
#   2. Prune in-progress entries whose tmux worker died (release as orphan)
#   3. Count active tmux sessions matching emf-worker-*
#   4. While count < MAX_PARALLEL and there's an eligible task: claim + spawn
#   5. Sleep TICK_SECONDS
#
# Workers run inside detached tmux sessions so the Mac side can attach for
# live debugging without fighting with this loop.
#
# Env (override via systemd unit Environment= or shell):
#   MAX_PARALLEL    default 3
#   TICK_SECONDS    default 30
#   EMF_REPO        default ~/GitHub/emf
#   EMF_QUEUE_REPO  default ~/GitHub/emf-queue
#   TMUX_PREFIX     default emf-worker

set -uo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SELF_DIR/lib/log.sh"
. "$SELF_DIR/lib/queue.sh"

MAX_PARALLEL="${MAX_PARALLEL:-3}"
TICK_SECONDS="${TICK_SECONDS:-30}"
EMF_REPO="${EMF_REPO:-$HOME/GitHub/emf}"
EMF_QUEUE_REPO="${EMF_QUEUE_REPO:-$HOME/GitHub/emf-queue}"
TMUX_PREFIX="${TMUX_PREFIX:-emf-worker}"

EMF_LOG_COMPONENT="dispatch"
log_init "dispatch"

if ! command -v tmux >/dev/null 2>&1; then
  log_error "tmux not found on PATH"
  exit 2
fi

active_sessions() {
  tmux ls 2>/dev/null | awk -F: -v p="$TMUX_PREFIX" '$1 ~ "^"p"-" {print $1}' | sort
}

prune_orphans() {
  local active dead_count=0 owner sess pr id pr_state
  active="$(active_sessions)"
  shopt -s nullglob
  for f in "$EMF_QUEUE_REPO"/in-progress/*.md; do
    owner="$(queue_get_field "$f" owner)"
    if [[ "$owner" != "${HOSTNAME:-$(hostname)}" ]]; then
      # Owned by another machine — leave it alone.
      continue
    fi
    sess="$TMUX_PREFIX-$(basename "$f" .md)"
    grep -qx "$sess" <<<"$active" && continue   # tmux session alive — not orphan

    # Tmux session gone, but the worker may have completed its push and
    # the PR may still be in flight (waiting on CI / auto-merge). Don't
    # release in that case — releasing would re-spawn a new worker that
    # races against the existing PR.
    pr="$(queue_get_field "$f" pr)"
    if [[ -n "$pr" && "$pr" != "null" ]]; then
      pr_state="$(gh pr view "$pr" -R cklinker/emf --json state --jq '.state' 2>/dev/null || echo UNKNOWN)"
      id="$(basename "$f" .md)"
      case "$pr_state" in
        OPEN)
          # PR still open: leave the task in in-progress until the PR
          # merges or closes, then mark accordingly.
          continue
          ;;
        MERGED)
          log_info "tmux gone but PR merged; archiving" task="$id" pr="$pr"
          queue_done "$f" "$pr" || true
          continue
          ;;
        CLOSED)
          log_warn "tmux gone and PR closed; releasing for retry" task="$id" pr="$pr"
          ;;
        *)
          log_warn "tmux gone, PR state $pr_state; releasing for retry" task="$id" pr="$pr"
          ;;
      esac
    fi

    log_warn "orphan detected, releasing" file="$f" session="$sess"
    queue_release_orphan "$f" || true
    dead_count=$((dead_count + 1))
  done
  shopt -u nullglob
  (( dead_count > 0 )) && log_info "released $dead_count orphan(s)"
}

spawn_worker() {
  local task_file="$1"
  local id sess
  id="$(basename "$task_file" .md)"
  sess="$TMUX_PREFIX-$id"

  log_event spawn task="$id" session="$sess"
  tmux new-session -d -s "$sess" \
    "EMF_REPO='$EMF_REPO' EMF_QUEUE_REPO='$EMF_QUEUE_REPO' bash '$SELF_DIR/worker.sh' '$task_file'; sleep 5"
}

main_loop() {
  log_event dispatch_start max_parallel="$MAX_PARALLEL" tick="$TICK_SECONDS"
  while :; do
    if ! git -C "$EMF_QUEUE_REPO" pull --rebase --autostash >/dev/null 2>&1; then
      log_warn "queue pull failed; will retry next tick"
    fi
    if ! git -C "$EMF_REPO" pull --rebase --autostash main >/dev/null 2>&1; then
      # Not fatal — workers fetch independently.
      :
    fi

    prune_orphans

    local active_count
    active_count="$(active_sessions | wc -l | tr -d ' ')"

    while (( active_count < MAX_PARALLEL )); do
      local claimed
      claimed="$("$SELF_DIR/claim.sh" --owner "${HOSTNAME:-$(hostname)}" 2>/dev/null)"
      if [[ -z "$claimed" || ! -f "$claimed" ]]; then
        break
      fi
      spawn_worker "$claimed"
      active_count=$((active_count + 1))
    done

    sleep "$TICK_SECONDS"
  done
}

main_loop
