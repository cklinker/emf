#!/usr/bin/env bash
# Structured logging for the dispatcher + workers. Emits one JSON object per
# line so Promtail/Loki can ingest cleanly.
#
# Usage from another script:
#   . .claude/dispatcher/lib/log.sh
#   log_init dispatch       # sets EMF_LOG_COMPONENT for all subsequent calls
#   log_info "starting loop" foo=bar count=3
#   log_error "claim failed" task=$id reason="$msg"
#   log_event task_claimed task=$id worker=$host
#
# Output sinks:
#   - stderr (always; lets you tail in tmux)
#   - $EMF_LOG_DIR/<component>.jsonl if EMF_LOG_DIR is set (default /var/log/emf-dispatcher)

EMF_LOG_DIR="${EMF_LOG_DIR:-/var/log/emf-dispatcher}"
EMF_LOG_COMPONENT="${EMF_LOG_COMPONENT:-uninit}"
EMF_LOG_TASK_ID="${EMF_LOG_TASK_ID:-}"

log_init() {
  EMF_LOG_COMPONENT="$1"
  if [[ -n "${EMF_LOG_DIR:-}" ]]; then
    mkdir -p "$EMF_LOG_DIR" 2>/dev/null || EMF_LOG_DIR=""
  fi
}

# Internal: emit one JSON line. Args: level, message, then key=value pairs.
_log_emit() {
  local level="$1"; shift
  local msg="$1"; shift
  local ts host pid line
  ts="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)"
  host="${HOSTNAME:-$(hostname)}"
  pid=$$

  # Build key=value pairs into JSON kv. Falls back gracefully if jq is missing.
  if command -v jq >/dev/null 2>&1; then
    local kv_args=()
    local k v
    for arg in "$@"; do
      k="${arg%%=*}"; v="${arg#*=}"
      kv_args+=(--arg "$k" "$v")
    done
    line="$(jq -nc \
      --arg ts "$ts" --arg host "$host" --arg pid "$pid" \
      --arg level "$level" --arg msg "$msg" \
      --arg component "$EMF_LOG_COMPONENT" --arg task "$EMF_LOG_TASK_ID" \
      "${kv_args[@]}" '
        ($ARGS.named | with_entries(select(.value != ""))) +
        {ts: $ts, host: $host, pid: ($pid|tonumber), level: $level,
         msg: $msg, component: $component, task: $task}
      ')"
  else
    # No jq: best-effort plain text.
    line="$ts $level [$EMF_LOG_COMPONENT${EMF_LOG_TASK_ID:+/$EMF_LOG_TASK_ID}] $msg $*"
  fi

  printf '%s\n' "$line" >&2
  if [[ -n "$EMF_LOG_DIR" && -d "$EMF_LOG_DIR" ]]; then
    printf '%s\n' "$line" >> "$EMF_LOG_DIR/${EMF_LOG_COMPONENT}.jsonl" 2>/dev/null || true
  fi
}

log_info()  { _log_emit info  "$@"; }
log_warn()  { _log_emit warn  "$@"; }
log_error() { _log_emit error "$@"; }
log_event() {
  # log_event <event_name> [k=v ...]
  local event="$1"; shift
  _log_emit event "$event" "$@"
}
