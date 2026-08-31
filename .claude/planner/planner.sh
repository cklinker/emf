#!/usr/bin/env bash
# Planner. Runs on worker-01 via systemd (rzware-planner.timer). Reads briefs
# from emf-queue/inbox/, emits structured tasks into emf-queue/ready/.
# Moved off the Mac 2026-08-31 per D-008 — RZWare's pipeline should not depend
# on hardware Craig does not own, or on a laptop being open.
#
# This is just a thin wrapper around `claude -p` + the system prompt in
# planner-prompt.md. The Claude session does the heavy lifting (reading briefs,
# searching the codebase, decomposing into tasks, writing files, linting).
#
# Singleton lock: only one planner runs at a time. If the previous run is
# still going (rare — should be done in <2 min), this one exits silently.
#
# Env (override via launchd plist EnvironmentVariables):
#   EMF_REPO        path to the EMF main repo (default ~/GitHub/emf)
#   EMF_QUEUE_REPO  path to emf-queue (default ~/GitHub/emf-queue)
#   CLAUDE_BIN      path to claude CLI (default 'claude' from PATH)
#   PLANNER_LOG_DIR default /srv/rzware-ceo/logs (promtail scrapes it)
#   PLANNER_MODEL   default sonnet (BUDGET.md — planning does not need Opus)
#   MAX_RUNTIME_SEC default 300 (5 min — kill the session if it overruns)

set -uo pipefail

EMF_REPO="${EMF_REPO:-$HOME/GitHub/emf}"
EMF_QUEUE_REPO="${EMF_QUEUE_REPO:-$HOME/GitHub/emf-queue}"
CLAUDE_BIN="${CLAUDE_BIN:-claude}"
PLANNER_LOG_DIR="${PLANNER_LOG_DIR:-/srv/rzware-ceo/logs}"
PLANNER_MODEL="${PLANNER_MODEL:-sonnet}"
# 300s killed a run mid-flight on 31 Aug: five briefs, one task emitted, the
# other four moved but the reasons and the commit never written. The watchdog
# should catch a hung session, not a busy one.
MAX_RUNTIME_SEC="${MAX_RUNTIME_SEC:-900}"

mkdir -p "$PLANNER_LOG_DIR"
LOG="$PLANNER_LOG_DIR/$(date -u +%Y-%m-%dT%H-%M-%SZ).jsonl"
LOCK="/tmp/emf-planner.lock"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }

# --- budget: honour the fleet-wide pause (BUDGET.md) ------------------------
# The dispatcher stops claiming when a worker hits a usage limit. The planner
# calls the same model on the same subscription, so it must stop too — otherwise
# the pause leaks and the planner keeps spending during it.
DISPATCH_LIB="${DISPATCH_LIB:-$EMF_REPO/.claude/dispatcher/lib/queue.sh}"
if [[ -r "$DISPATCH_LIB" ]]; then
  # shellcheck source=/dev/null
  . "$DISPATCH_LIB" 2>/dev/null || true
  if declare -F throttled >/dev/null && throttled; then
    log "fleet paused until $(cat "${PAUSE_FILE:-/srv/rzware-ceo/state/PAUSED_UNTIL}" 2>/dev/null); skipping"
    exit 0
  fi
fi

# --- Singleton lock ---------------------------------------------------------
if ! ( set -o noclobber; echo $$ > "$LOCK" ) 2>/dev/null; then
  prev_pid="$(cat "$LOCK" 2>/dev/null || echo)"
  if [[ -n "$prev_pid" ]] && kill -0 "$prev_pid" 2>/dev/null; then
    log "previous planner still running (pid $prev_pid); exiting"
    exit 0
  fi
  log "stale lock (pid $prev_pid not alive); reclaiming"
  rm -f "$LOCK"
  echo $$ > "$LOCK"
fi
trap 'rm -f "$LOCK"' EXIT

# --- Pull latest queue ------------------------------------------------------
# Retried, because a single failure used to skip the whole 15-minute cycle. The
# dispatcher pulls this same repo every 30s and holds .git/index.lock while it
# does, so a collision is routine rather than exceptional — on 31 Aug two of
# four consecutive runs aborted here and the queue sat unplanned for an hour.
pull_queue() {
  local attempt
  for attempt in 1 2 3; do
    if git -C "$EMF_QUEUE_REPO" pull --rebase --autostash >/dev/null 2>&1; then
      (( attempt > 1 )) && log "queue pull succeeded on attempt $attempt"
      return 0
    fi
    # A lock from a dispatcher tick clears in seconds; back off and retry.
    (( attempt < 3 )) && sleep $(( attempt * 5 ))
  done
  return 1
}
if ! pull_queue; then
  log "queue pull failed after 3 attempts; aborting"
  exit 1
fi

# --- Bail if nothing to plan -----------------------------------------------
shopt -s nullglob
INBOX_FILES=("$EMF_QUEUE_REPO"/inbox/*.md)
shopt -u nullglob
if (( ${#INBOX_FILES[@]} == 0 )); then
  log "inbox empty; nothing to plan"
  exit 0
fi
log "found ${#INBOX_FILES[@]} inbox file(s); invoking planner session"

# --- Build the user prompt --------------------------------------------------
USER_PROMPT="$(cat <<EOF
Today's date is $(date -u +%Y-%m-%d). Use it for task ids.

Process the inbox files in $EMF_QUEUE_REPO/inbox/.

Files to consider:
$(printf '  - %s\n' "${INBOX_FILES[@]}")

Follow the system prompt rules. Emit tasks to ready/, lint each one, move processed briefs to inbox/_processed/, then commit + push once. Report a one-line summary per file you touched.
EOF
)"

# --- Run the planner session -----------------------------------------------
PROMPT_FILE="$EMF_REPO/.claude/planner/planner-prompt.md"
[[ -f "$PROMPT_FILE" ]] || { log "missing planner-prompt.md at $PROMPT_FILE"; exit 1; }

cd "$EMF_QUEUE_REPO" || exit 1

# Pick a timeout wrapper. Linux has 'timeout'; macOS only has 'gtimeout'
# (from coreutils via Homebrew) if anything. Fall back to a watchdog
# implemented in shell so the planner remains portable.
TIMEOUT_BIN=""
if command -v timeout  >/dev/null 2>&1; then TIMEOUT_BIN="timeout";
elif command -v gtimeout >/dev/null 2>&1; then TIMEOUT_BIN="gtimeout";
fi

# Defensively unset CLAUDECODE so claude doesn't refuse to launch when this
# script is invoked from inside an existing Claude Code session (e.g. during
# a manual test). Launchd-triggered runs don't have CLAUDECODE set anyway.
unset CLAUDECODE

if [[ -n "$TIMEOUT_BIN" ]]; then
  "$TIMEOUT_BIN" --preserve-status "$MAX_RUNTIME_SEC" \
    "$CLAUDE_BIN" \
      --model "$PLANNER_MODEL" \
      -p "$USER_PROMPT" \
      --append-system-prompt "$(cat "$PROMPT_FILE")" \
      --output-format stream-json \
      --include-partial-messages \
      --verbose \
      --dangerously-skip-permissions \
    >> "$LOG" 2>&1
  RC=$?
else
  # Watchdog fallback: launch claude in background, kill after MAX_RUNTIME_SEC.
  "$CLAUDE_BIN" \
    --model "$PLANNER_MODEL" \
    -p "$USER_PROMPT" \
    --append-system-prompt "$(cat "$PROMPT_FILE")" \
    --output-format stream-json \
    --include-partial-messages \
    --verbose \
    --dangerously-skip-permissions \
    >> "$LOG" 2>&1 &
  CLAUDE_PID=$!
  ( sleep "$MAX_RUNTIME_SEC"; kill -TERM "$CLAUDE_PID" 2>/dev/null; sleep 5; kill -KILL "$CLAUDE_PID" 2>/dev/null ) &
  WATCHDOG_PID=$!
  wait "$CLAUDE_PID"
  RC=$?
  kill "$WATCHDOG_PID" 2>/dev/null
fi

if (( RC == 0 )); then
  log "planner session ok (log: $LOG)"
else
  log "planner session failed (rc=$RC, log: $LOG)"
fi
exit "$RC"
