# Contributing

Kelta Platform development runs through an autopilot loop: tasks land in a queue, the dispatcher on `worker-01` claims them, workers open PRs, and CI auto-merges on green. This document covers the day-to-day surface area. For the full system design see [`.claude/dispatcher/README.md`](.claude/dispatcher/README.md).

## How work gets done

### 1. Free-form ideas — use `inbox/`

Drop a brief markdown file in `~/GitHub/emf-queue/inbox/<slug>.md`. No frontmatter required — write the idea however it makes sense. The planner picks it up within 10 min, decomposes it into one or more structured task files, and stages them in `ready/`.

### 2. Curated tasks — use `ready/` → `approved/`

When you already know what needs to happen, write the task markdown directly into `~/GitHub/emf-queue/ready/`. Use the frontmatter schema from any existing task in that directory (id, title, type, priority, parallel_safe, needs_migration, etc.). Review it, then promote with:

```sh
cd ~/GitHub/emf-queue
git mv ready/TASK-XXXX.md approved/TASK-XXXX.md
git commit -m "approve TASK-XXXX"
git push
```

Only files in `approved/` are eligible for dispatch.

### 3. Dispatch — automatic

The dispatcher runs as `emf-dispatcher.service` on `worker-01` (`craig@192.168.0.232`). It polls the queue, claims up to `MAX_PARALLEL=3` tasks concurrently, and spawns a `claude -p` worker per task in its own git worktree under `/var/lib/emf-wt/<id>`. Live workers run inside tmux sessions named `emf-worker-<id>` — attach read-only with `tmux attach -r -t emf-worker-<id>`.

### 4. PRs — `autopilot` label, auto-merge on green

Workers push their branch and open a PR via `gh pr create --label autopilot --fill`. The `autopilot` label triggers the auto-merge workflow, which squash-merges as soon as CI is green. The worker polls `gh pr checks` for up to 30 min:

- **CI green** → PR merges, task moves to `done/`, worktree is removed.
- **CI red** → worker is re-launched with the failure context, up to `max_attempts` (default 3).
- **Attempts exhausted** → task is moved to `failed/` with the diagnostic.

You don't need to babysit anything. Watch progress with `cat /tmp/emf-status.txt` (Mac) or `journalctl -fu emf-dispatcher -o cat` (worker-01).

### 5. Pause everything

```sh
ssh worker-01
sudo systemctl stop emf-dispatcher
```

In-flight workers in tmux keep running to completion; no new tasks are claimed. Resume with `sudo systemctl start emf-dispatcher`. To drain without stopping the service, set `MAX_PARALLEL=0` in the unit and reload.

## Manual contributions

You can still work the old-fashioned way — branch off `main`, push, open a PR. Just don't add the `autopilot` label unless the PR was produced by a worker. See the **Task Workflow** section of [`CLAUDE.md`](CLAUDE.md) for branch naming, commit style, and the pre-PR checklist.

## Hard rules

- **Never commit to `main`.** All changes go through a PR.
- **Never use `--no-verify` or `git push --force` against `main`.** Hooks will block; if you hit a real problem, fix the root cause.
- **Run `/verify` before requesting review** on a manual PR. CI runs the same checks.
- **Multi-pod NATS rule** for in-memory registries — see [`CLAUDE.md`](CLAUDE.md).

## Recovery

If a task gets stuck or you need to release it manually:

```sh
cd ~/GitHub/emf-queue
git mv in-progress/TASK-XXXX.md approved/TASK-XXXX.md
git commit -m "release TASK-XXXX"
git push
```

The dispatcher prunes dead tmux sessions on its next tick and re-claims.
