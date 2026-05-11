# EMF autopilot dispatcher

Phase 1 of the autonomous feature lifecycle planned in [`~/.claude/plans/i-want-to-maximize-sleepy-karp.md`](../../). Runs on `worker-01` (Linux, `craig@192.168.0.232`) as a systemd service. Mac side runs `status.sh` via launchd for visibility.

## Pieces

| File | Role |
|---|---|
| `dispatch.sh` | Outer loop on worker-01: pull queue, prune dead workers, claim + spawn up to `MAX_PARALLEL` |
| `worker.sh` | Per-task: worktree → `claude -p` with worker-prompt → `/verify` → push → `gh pr create` → poll CI → archive |
| `claim.sh` | Atomic claim of the next eligible task from `approved/` |
| `worker-prompt.md` | System prompt appended to every `claude -p` worker session |
| `lib/queue.sh` | Frontmatter rw, atomic claim, push-with-retry, eligibility filter |
| `lib/log.sh` | Structured JSON logging to stderr + `$EMF_LOG_DIR/<component>.jsonl` |
| `status.sh` | One-screen queue summary; safe on either machine |
| `systemd/emf-dispatcher.service` | systemd unit for `worker-01` |
| `launchd/com.cklinker.emf-planner.plist` | every-10-min planner run on Mac |
| `launchd/com.cklinker.emf-status.plist` | every-1-min status refresh on Mac |
| `launchd/install-launchd.sh` | idempotent installer for both agents |

## Install on worker-01

```sh
ssh worker-01

# 1. Pull the latest emf + emf-queue
cd ~/GitHub/emf       && git pull
cd ~/GitHub/emf-queue && git pull

# 2. Prepare runtime dirs
sudo install -d -o craig -g craig /var/lib/emf-wt /var/log/emf-dispatcher

# 3. Install + enable the systemd unit
sudo cp ~/GitHub/emf/.claude/dispatcher/systemd/emf-dispatcher.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now emf-dispatcher.service

# 4. Watch it run
journalctl -fu emf-dispatcher -o cat
```

## Install on Mac

```sh
# Installs both plists, reloads launchd, verifies registration. Idempotent —
# re-run any time a plist changes. A plain `cp` is NOT enough; launchd holds
# the old definition until it is booted out + bootstrapped.
bash ~/GitHub/emf/.claude/dispatcher/launchd/install-launchd.sh

# Verify
cat /tmp/emf-status.txt          # refreshes every minute
tail -f /tmp/emf-planner.launchd.log

# Add an SSH config entry for worker-01 if not already
cat >> ~/.ssh/config <<'EOF'
Host worker-01
  HostName 192.168.0.232
  User craig
EOF
```

## Live debugging

```sh
ssh worker-01

# List active workers
tmux ls

# Attach to one (read-only — Ctrl+b d to detach)
tmux attach -r -t emf-worker-TASK-2026-05-10-0001

# Tail dispatcher log
journalctl -fu emf-dispatcher -o cat

# Tail per-task log
tail -f /var/log/emf-dispatcher/TASK-2026-05-10-0001.jsonl | jq -c .
```

## Safety knobs

- `MAX_PARALLEL=3` (env on systemd unit) — set to `0` to drain without claiming new work
- `systemctl stop emf-dispatcher` — stops claiming; in-flight workers in tmux finish naturally
- Move a task back: `cd ~/GitHub/emf-queue && git mv in-progress/TASK-X.md approved/TASK-X.md && git commit -m "manual release" && git push`
- Pause everything: rename `approved/` to `approved.paused/` (the dispatcher's eligibility query returns nothing)

## Failure modes + recovery

| Symptom | Cause | Fix |
|---|---|---|
| Task stuck in `in-progress/` | tmux session died | Dispatcher prunes within one tick; manual: `queue_release_orphan` or `git mv in-progress/X.md approved/X.md` |
| Two workers grabbed same task | Push race | Won't happen with `queue_push_with_retry` (commit + push, rebase on reject) |
| PR opens but CI never runs | No runners online | `gh api /repos/cklinker/emf/actions/runners` — should show 12 idle on fc17 |
| Migration collision | Two `needs_migration: true` tasks raced | Dispatcher's eligibility filter blocks if `_active-migration` exists; manual: `rm` the marker after manual fix |
| Worker burns retries | Buggy task brief | Move to `failed/`, rewrite the brief in `inbox/`, re-promote via planner |

## What this does NOT do (Phase 2+)

- Planner: turn free-form `inbox/` briefs into structured `ready/` tasks
- Plan-file linter: enforce frontmatter schema
- Token-spend telemetry: per-task Anthropic cost rollup
- Grafana dashboard
