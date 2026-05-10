#!/usr/bin/env bash
# Per-task worker. Runs ONE task end-to-end:
#   1. Create worktree at $EMF_WT_ROOT/<task-id> on a fresh branch off origin/main
#   2. Invoke `claude -p` with the worker-prompt, passing the task brief
#   3. Run /verify (defensive — the worker also runs it inside the session)
#   4. Push branch, open PR with autopilot label
#   5. Poll `gh pr checks` until conclusion
#   6. On success: queue_done; on retryable failure: re-loop up to max_attempts
#   7. On exhausted retries: queue_fail; always: remove the worktree
#
# Designed to run inside a tmux session named emf-worker-<task-id> so the
# Mac side can attach for live debugging.
#
# Usage:
#   .claude/dispatcher/worker.sh <task-file>
#
# Env:
#   EMF_REPO        path to the EMF main repo (default ~/GitHub/emf)
#   EMF_QUEUE_REPO  path to emf-queue (default ~/GitHub/emf-queue)
#   EMF_WT_ROOT     worktree root (default /var/lib/emf-wt)
#   CLAUDE_BIN      claude CLI path (default 'claude' from PATH)
#   PR_TIMEOUT_MIN  how long to wait for CI (default 30)

set -uo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SELF_DIR/lib/log.sh"
. "$SELF_DIR/lib/queue.sh"

EMF_REPO="${EMF_REPO:-$HOME/GitHub/emf}"
EMF_QUEUE_REPO="${EMF_QUEUE_REPO:-$HOME/GitHub/emf-queue}"
EMF_WT_ROOT="${EMF_WT_ROOT:-/var/lib/emf-wt}"
CLAUDE_BIN="${CLAUDE_BIN:-claude}"
PR_TIMEOUT_MIN="${PR_TIMEOUT_MIN:-30}"

TASK_FILE="${1:-}"
[[ -n "$TASK_FILE" && -f "$TASK_FILE" ]] || { echo "Usage: $0 <task-file>" >&2; exit 2; }

EMF_LOG_COMPONENT="worker"
log_init "worker"
ID="$(basename "$TASK_FILE" .md)"
EMF_LOG_TASK_ID="$ID"

BRANCH="$(queue_get_field "$TASK_FILE" branch)"
[[ -z "$BRANCH" ]] && BRANCH="autopilot/$ID"
WT="$EMF_WT_ROOT/$ID"
ATTEMPTS="$(queue_get_field "$TASK_FILE" attempts)"; ATTEMPTS="${ATTEMPTS:-1}"
MAX_ATTEMPTS="$(queue_get_field "$TASK_FILE" max_attempts)"; MAX_ATTEMPTS="${MAX_ATTEMPTS:-3}"

cleanup_worktree() {
  if [[ -d "$WT" ]]; then
    log_info "removing worktree" path="$WT"
    git -C "$EMF_REPO" worktree remove --force "$WT" 2>/dev/null || rm -rf "$WT"
  fi
}
trap cleanup_worktree EXIT

# ---- 1. Worktree ------------------------------------------------------------

log_event worker_start task="$ID" attempts="$ATTEMPTS" max="$MAX_ATTEMPTS" branch="$BRANCH"

if ! git -C "$EMF_REPO" fetch --quiet origin main; then
  log_error "git fetch failed"
  queue_fail "$TASK_FILE" "git fetch origin main failed"
  exit 1
fi

mkdir -p "$EMF_WT_ROOT"
# Belt and suspenders cleanup before re-creating: any stale worktree dir,
# stale worktree registration in .git/worktrees, stale local branch, stale
# remote branch from a prior failed attempt.
if [[ -d "$WT" ]]; then
  log_warn "stale worktree dir exists, removing" path="$WT"
  git -C "$EMF_REPO" worktree remove --force "$WT" 2>/dev/null
  rm -rf "$WT"
fi
git -C "$EMF_REPO" worktree prune >/dev/null 2>&1
if git -C "$EMF_REPO" show-ref --verify --quiet "refs/heads/$BRANCH"; then
  log_warn "stale local branch exists, deleting" branch="$BRANCH"
  git -C "$EMF_REPO" branch -D "$BRANCH" >/dev/null 2>&1
fi
if git -C "$EMF_REPO" ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
  log_warn "stale remote branch exists, deleting" branch="$BRANCH"
  git -C "$EMF_REPO" push origin --delete "$BRANCH" >/dev/null 2>&1
fi

# -B (force-create) so a half-cleaned-up branch from a previous attempt
# doesn't block the new worktree.
if ! git -C "$EMF_REPO" worktree add --force "$WT" -B "$BRANCH" origin/main 2>&1; then
  log_error "worktree add failed"
  queue_fail "$TASK_FILE" "worktree add failed"
  exit 1
fi
log_info "worktree created" path="$WT"

# ---- 2. Claude session ------------------------------------------------------

WORKER_PROMPT="$WT/.claude/dispatcher/worker-prompt.md"
[[ -f "$WORKER_PROMPT" ]] || { log_error "worker-prompt missing" path="$WORKER_PROMPT"; queue_fail "$TASK_FILE" "worker-prompt.md missing in worktree"; exit 1; }

USER_PROMPT="$(cat <<EOF
Begin task ${ID}.

Task file (already read by you via \$EMF_TASK_FILE = ${TASK_FILE}):

\`\`\`
$(cat "$TASK_FILE")
\`\`\`

Work in the current directory ($WT). Stop only when /verify is green and you have followed all hard rules in the worker-prompt. Do not push or open a PR — the wrapper does that.
EOF
)"

cd "$WT" || { log_error "cd to worktree failed"; queue_fail "$TASK_FILE" "cd worktree failed"; exit 1; }

JSONL_LOG="${EMF_LOG_DIR:-/var/log/emf-dispatcher}/${ID}.jsonl"
mkdir -p "$(dirname "$JSONL_LOG")" 2>/dev/null || true

log_event claude_start task="$ID" log="$JSONL_LOG"
EMF_TASK_FILE="$TASK_FILE" \
  "$CLAUDE_BIN" \
    -p "$USER_PROMPT" \
    --append-system-prompt "$(cat "$WORKER_PROMPT")" \
    --output-format stream-json \
    --include-partial-messages \
    --verbose \
    --dangerously-skip-permissions \
  >> "$JSONL_LOG" 2>&1
CLAUDE_RC=$?
log_event claude_end task="$ID" rc="$CLAUDE_RC"

# ---- 3. Self-blocked check --------------------------------------------------

if [[ -f "$WT/BLOCKED.md" ]]; then
  reason="$(cat "$WT/BLOCKED.md")"
  log_warn "worker self-blocked" reason="${reason:0:100}"
  queue_fail "$TASK_FILE" "BLOCKED: ${reason:0:200}"
  exit 0
fi

# ---- 4. (No defensive /verify) ----------------------------------------------
# The Stop hook .claude/hooks/pre-pr-gate.sh already runs verify.sh before
# the claude session can return. Running it again here was costing 5–10 min
# per task with no signal — if the Stop hook passed, verify is green.

# ---- 5. Push + PR -----------------------------------------------------------

# If nothing changed, the worker did nothing useful. Treat as failure.
if git -C "$WT" diff --quiet origin/main && [[ -z "$(git -C "$WT" status --porcelain)" ]]; then
  log_error "no changes after worker session"
  if (( ATTEMPTS < MAX_ATTEMPTS )); then
    queue_release_orphan "$TASK_FILE"
  else
    queue_fail "$TASK_FILE" "worker produced no diff after $MAX_ATTEMPTS attempts"
  fi
  exit 1
fi

# Stage anything left unstaged + create a final commit if needed.
git -C "$WT" add -A
if ! git -C "$WT" diff --cached --quiet; then
  git -C "$WT" commit -m "autopilot: $ID — final commit by worker.sh" >/dev/null
fi

if ! git -C "$WT" push -u origin "$BRANCH" 2>&1; then
  log_error "git push failed"
  queue_fail "$TASK_FILE" "git push origin $BRANCH failed"
  exit 1
fi
log_event branch_pushed task="$ID" branch="$BRANCH"

PR_URL="$(gh pr create --label autopilot --fill --head "$BRANCH" 2>&1 | tail -1)"
if [[ "$PR_URL" != https://github.com/* ]]; then
  log_error "gh pr create failed" output="$PR_URL"
  queue_fail "$TASK_FILE" "gh pr create failed: ${PR_URL:0:120}"
  exit 1
fi
PR_NUM="${PR_URL##*/}"
log_event pr_opened task="$ID" pr="$PR_NUM" url="$PR_URL"
queue_set_field "$TASK_FILE" pr "$PR_NUM"

# ---- 6. Poll CI -------------------------------------------------------------

deadline=$(( $(date +%s) + PR_TIMEOUT_MIN * 60 ))
poll_interval=30
final_state=""

while (( $(date +%s) < deadline )); do
  raw="$(gh pr view "$PR_NUM" --json state,mergedAt,statusCheckRollup 2>/dev/null)"
  state="$(printf '%s' "$raw" | jq -r '.state // "UNKNOWN"')"
  merged_at="$(printf '%s' "$raw" | jq -r '.mergedAt // ""')"

  if [[ "$state" == "MERGED" || -n "$merged_at" ]]; then
    final_state="MERGED"; break
  fi
  if [[ "$state" == "CLOSED" ]]; then
    final_state="CLOSED"; break
  fi

  failures="$(printf '%s' "$raw" | jq -r '
    [.statusCheckRollup[]?
     | select((.conclusion // .state // "") | ascii_downcase
              | IN("failure","failed","cancelled","timed_out","action_required","startup_failure"))
     | (.name // .context // "?")
    ] | join(",")
  ')"
  if [[ -n "$failures" && "$failures" != "" ]]; then
    final_state="CHECK_FAIL"
    log_warn "ci checks failed" failures="$failures"
    break
  fi

  sleep "$poll_interval"
done

if [[ -z "$final_state" ]]; then
  final_state="TIMEOUT"
  log_warn "ci poll timed out" minutes="$PR_TIMEOUT_MIN"
fi

# ---- 7. Archive -------------------------------------------------------------

case "$final_state" in
  MERGED)
    log_event task_done task="$ID" pr="$PR_NUM"
    queue_done "$TASK_FILE" "$PR_NUM"
    ;;
  CHECK_FAIL|CLOSED|TIMEOUT)
    if (( ATTEMPTS < MAX_ATTEMPTS )); then
      log_info "releasing for retry" final_state="$final_state" attempts="$ATTEMPTS" max="$MAX_ATTEMPTS"
      # Close the PR so the next attempt opens a fresh one.
      gh pr close "$PR_NUM" --comment "autopilot retry: closing to relaunch on attempt $((ATTEMPTS + 1))" >/dev/null 2>&1 || true
      git -C "$EMF_REPO" push origin --delete "$BRANCH" >/dev/null 2>&1 || true
      queue_release_orphan "$TASK_FILE"
    else
      log_error "exhausted retries" final_state="$final_state" attempts="$ATTEMPTS"
      queue_fail "$TASK_FILE" "$final_state after $MAX_ATTEMPTS attempts (pr #$PR_NUM)"
    fi
    ;;
  *)
    queue_fail "$TASK_FILE" "unexpected final state: $final_state"
    ;;
esac
