#!/usr/bin/env bash
# migration-claim.sh — atomically pick the next Flyway sequence number.
#
# Considers two sources of "in-flight" sequence numbers:
#   1. Migrations that exist on origin/main (already merged).
#   2. Migrations claimed by other in-progress tasks in emf-queue/in-progress/.
#
# Returns the next free V<N> on stdout, and (unless --dry-run) writes a claim
# marker into the in-progress task file so that other workers running this
# script see the reservation before they push a colliding migration.
#
# Usage:
#   scripts/migration-claim.sh [--task-file PATH] [--dry-run]
#
# Env:
#   EMF_REPO        Path to the EMF main repo. Default: $PWD if it contains kelta-worker, else ~/GitHub/emf.
#   EMF_QUEUE_REPO  Path to the emf-queue repo.   Default: ~/GitHub/emf-queue.

set -euo pipefail

EMF_REPO="${EMF_REPO:-}"
EMF_QUEUE_REPO="${EMF_QUEUE_REPO:-$HOME/GitHub/emf-queue}"
TASK_FILE=""
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --task-file) TASK_FILE="$2"; shift 2 ;;
    --dry-run)   DRY_RUN=1; shift ;;
    -h|--help)
      sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$EMF_REPO" ]]; then
  if [[ -d "$PWD/kelta-worker" ]]; then
    EMF_REPO="$PWD"
  else
    EMF_REPO="$HOME/GitHub/emf"
  fi
fi

MIGRATION_DIR="$EMF_REPO/kelta-worker/src/main/resources/db/migration"
[[ -d "$MIGRATION_DIR" ]] || { echo "Not found: $MIGRATION_DIR" >&2; exit 3; }

# 1. Highest sequence already on origin/main.
git -C "$EMF_REPO" fetch --quiet origin main
merged_max=$(
  git -C "$EMF_REPO" ls-tree --name-only -r origin/main \
    -- "kelta-worker/src/main/resources/db/migration/" \
  | xargs -n1 basename 2>/dev/null \
  | grep -oE '^V[0-9]+' \
  | sed 's/^V//' \
  | sort -n | tail -1 || true
)
merged_max="${merged_max:-0}"

# 2. Highest sequence claimed by any in-progress task.
claimed_max=0
if [[ -d "$EMF_QUEUE_REPO/in-progress" ]]; then
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    n=$(grep -oE 'claimed_migration:[[:space:]]*V[0-9]+' "$f" 2>/dev/null \
        | grep -oE '[0-9]+' | tail -1 || true)
    if [[ -n "$n" && "$n" -gt "$claimed_max" ]]; then
      claimed_max="$n"
    fi
  done < <(find "$EMF_QUEUE_REPO/in-progress" -maxdepth 1 -type f -name '*.md' 2>/dev/null)
fi

next=$(( merged_max > claimed_max ? merged_max + 1 : claimed_max + 1 ))
next_v="V${next}"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "$next_v"
  exit 0
fi

# Write the claim marker into the task file's frontmatter, between '---' delimiters.
if [[ -n "$TASK_FILE" ]]; then
  [[ -f "$TASK_FILE" ]] || { echo "Task file not found: $TASK_FILE" >&2; exit 4; }
  if grep -q '^claimed_migration:' "$TASK_FILE"; then
    sed -i.bak -E "s|^claimed_migration:.*|claimed_migration: ${next_v}|" "$TASK_FILE"
  else
    awk -v claim="claimed_migration: ${next_v}" '
      BEGIN{seen=0}
      /^---[[:space:]]*$/{
        if (seen==0) { print; seen=1; next }
        if (seen==1) { print claim; print; seen=2; next }
      }
      {print}
    ' "$TASK_FILE" > "$TASK_FILE.tmp" && mv "$TASK_FILE.tmp" "$TASK_FILE"
  fi
  rm -f "$TASK_FILE.bak"
fi

echo "$next_v"
