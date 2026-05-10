#!/usr/bin/env bash
# Queue manipulation primitives for the dispatcher and workers.
# All functions assume EMF_QUEUE_REPO points at a git working tree with the
# six lifecycle dirs (inbox/ready/approved/in-progress/done/failed).
#
# Source this file from another script:
#   . .claude/dispatcher/lib/queue.sh
#
# Functions:
#   queue_pull                    — git pull --rebase --autostash
#   queue_push_with_retry MSG     — commit (msg) and push, retry on race
#   queue_get_field FILE FIELD    — read scalar frontmatter field
#   queue_set_field FILE FIELD VAL— upsert scalar frontmatter field
#   queue_eligible_tasks          — list approved/*.md eligible to claim
#   queue_claim FILE              — atomic mv approved → in-progress
#   queue_done FILE PR_NUM        — atomic mv in-progress → done
#   queue_fail FILE REASON        — atomic mv in-progress → failed
#   queue_release_orphan FILE     — mv in-progress → approved (worker died)

EMF_QUEUE_REPO="${EMF_QUEUE_REPO:-$HOME/GitHub/emf-queue}"

# ---- Internal helpers --------------------------------------------------------

_q() { git -C "$EMF_QUEUE_REPO" "$@"; }

_q_in_progress_count_by_owner() {
  local owner="$1"
  local n=0 f
  for f in "$EMF_QUEUE_REPO"/in-progress/*.md; do
    [[ -e "$f" ]] || continue
    [[ "$(queue_get_field "$f" owner)" == "$owner" ]] && n=$((n+1))
  done
  echo "$n"
}

# ---- Public API --------------------------------------------------------------

queue_pull() {
  _q pull --rebase --autostash 2>&1 || return 1
}

queue_push_with_retry() {
  local msg="$1"
  local tries=0
  while (( tries < 3 )); do
    _q add -A
    if _q diff --cached --quiet; then
      return 0   # nothing to commit
    fi
    _q commit -m "$msg" >/dev/null 2>&1 || return 2
    if _q push 2>/dev/null; then
      return 0
    fi
    tries=$((tries+1))
    _q pull --rebase --autostash >/dev/null 2>&1 || return 3
  done
  return 4
}

# Read a scalar frontmatter field: 'foo: bar' → echoes 'bar'.
queue_get_field() {
  local file="$1" field="$2"
  awk -v f="$field" '
    BEGIN{in_fm=0}
    /^---[[:space:]]*$/{ in_fm = (in_fm ? 0 : 1); next }
    in_fm && $0 ~ "^"f":[[:space:]]" {
      sub("^"f":[[:space:]]*", "")
      sub(/^"/, ""); sub(/"$/, "")
      sub(/^'\''/, ""); sub(/'\''$/, "")
      print; exit
    }
  ' "$file"
}

# Upsert a scalar frontmatter field. Inserts before the closing '---' if
# the field doesn't exist.
queue_set_field() {
  local file="$1" field="$2" val="$3"
  if grep -q "^${field}:" "$file" 2>/dev/null; then
    sed -i.bak -E "s|^${field}:.*|${field}: ${val}|" "$file" && rm -f "${file}.bak"
  else
    awk -v line="${field}: ${val}" '
      BEGIN{seen=0; inserted=0}
      /^---[[:space:]]*$/{
        if (seen==0){ print; seen=1; next }
        if (seen==1 && !inserted){ print line; inserted=1 }
      }
      {print}
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file"
  fi
}

# List approved tasks in claim order. Filters:
#   - status: approved
#   - depends_on: every dep id must be in done/
#   - parallel_safe: false → only if no other in-progress task exists
#   - needs_migration: true → only if _active-migration marker absent
# Sort: priority asc, then created_at asc.
queue_eligible_tasks() {
  local active_in_progress=0
  active_in_progress=$(ls "$EMF_QUEUE_REPO"/in-progress/*.md 2>/dev/null | wc -l | tr -d ' ')
  local migration_locked="false"
  [[ -f "$EMF_QUEUE_REPO/_active-migration" ]] && migration_locked="true"

  local tmp
  tmp="$(mktemp -t qeligible.XXXX)"
  local f
  for f in "$EMF_QUEUE_REPO"/approved/*.md; do
    [[ -e "$f" ]] || continue
    local id pri created par_safe needs_mig deps eligible=1
    id="$(queue_get_field "$f" id)"
    pri="$(queue_get_field "$f" priority)"; pri="${pri:-5}"
    created="$(queue_get_field "$f" created_at)"
    par_safe="$(queue_get_field "$f" parallel_safe)"
    needs_mig="$(queue_get_field "$f" needs_migration)"
    deps="$(queue_get_field "$f" depends_on)"

    # Migration lock
    if [[ "$needs_mig" == "true" && "$migration_locked" == "true" ]]; then
      eligible=0
    fi
    # Parallel-safe check: if any other in-progress task exists and this one
    # isn't parallel_safe, skip.
    if [[ "$par_safe" == "false" && "$active_in_progress" -gt 0 ]]; then
      eligible=0
    fi
    # Dependency check: every id in depends_on must be in done/.
    if [[ -n "$deps" && "$deps" != "[]" ]]; then
      local d_ids
      d_ids="$(printf '%s\n' "$deps" | tr -d '[]" ' | tr ',' '\n')"
      local d
      for d in $d_ids; do
        [[ -z "$d" ]] && continue
        if [[ ! -f "$EMF_QUEUE_REPO/done/$d.md" ]]; then
          eligible=0; break
        fi
      done
    fi
    if (( eligible == 1 )); then
      printf '%s\t%s\t%s\n' "$pri" "$created" "$f" >> "$tmp"
    fi
  done

  sort -k1,1n -k2,2 "$tmp" | cut -f3
  rm -f "$tmp"
}

# Atomic claim. Echoes the new in-progress path, or empty + nonzero exit on race.
queue_claim() {
  local src="$1"
  local owner="${2:-${HOSTNAME:-$(hostname)}}"
  local base id dest branch attempts
  base="$(basename "$src")"
  id="${base%.md}"
  dest="$EMF_QUEUE_REPO/in-progress/$base"
  branch="autopilot/$id"
  attempts="$(queue_get_field "$src" attempts)"; attempts="${attempts:-0}"

  queue_pull >/dev/null 2>&1 || return 1
  if [[ ! -f "$src" ]]; then
    return 2   # someone else claimed it during pull
  fi

  _q mv "approved/$base" "in-progress/$base"
  queue_set_field "$dest" status "in_progress"
  queue_set_field "$dest" owner "$owner"
  queue_set_field "$dest" branch "$branch"
  queue_set_field "$dest" attempts "$((attempts + 1))"

  if queue_push_with_retry "claim $id by $owner (attempt $((attempts + 1)))"; then
    echo "$dest"
    return 0
  fi
  return 3
}

queue_done() {
  local src="$1" pr="$2"
  local base id dest
  base="$(basename "$src")"
  id="${base%.md}"
  dest="$EMF_QUEUE_REPO/done/$base"

  _q mv "in-progress/$base" "done/$base"
  queue_set_field "$dest" status "done"
  [[ -n "$pr" ]] && queue_set_field "$dest" pr "$pr"

  # If this task held the migration lock, release it.
  local needs_mig
  needs_mig="$(queue_get_field "$dest" needs_migration)"
  if [[ "$needs_mig" == "true" && -f "$EMF_QUEUE_REPO/_active-migration" ]]; then
    _q rm -f "_active-migration" >/dev/null 2>&1
  fi

  queue_push_with_retry "done $id (pr #$pr)"
}

queue_fail() {
  local src="$1" reason="$2"
  local base id dest
  base="$(basename "$src")"
  id="${base%.md}"
  dest="$EMF_QUEUE_REPO/failed/$base"

  _q mv "in-progress/$base" "failed/$base"
  queue_set_field "$dest" status "failed"
  queue_set_field "$dest" fail_reason "${reason//$'\n'/ }"

  # Release migration lock if held.
  local needs_mig
  needs_mig="$(queue_get_field "$dest" needs_migration)"
  if [[ "$needs_mig" == "true" && -f "$EMF_QUEUE_REPO/_active-migration" ]]; then
    _q rm -f "_active-migration" >/dev/null 2>&1
  fi

  queue_push_with_retry "fail $id: ${reason:0:80}"
}

queue_release_orphan() {
  local src="$1"
  local base id
  base="$(basename "$src")"
  id="${base%.md}"
  _q mv "in-progress/$base" "approved/$base" 2>/dev/null
  queue_set_field "$EMF_QUEUE_REPO/approved/$base" status "approved"
  queue_set_field "$EMF_QUEUE_REPO/approved/$base" owner "null"
  queue_push_with_retry "release orphan $id (worker died)"
}
