#!/usr/bin/env bash
# Slack notification helpers for the EMF dispatcher.
# Source this file AFTER lib/log.sh to get notify_slack.
#
# Usage:
#   . "$SELF_DIR/lib/notify.sh"
#   notify_slack "#rzware-ceo" "Task TASK-… failed"
#
# On any error (sops missing, network down, curl non-zero): logs a warning
# via log_warn (requires lib/log.sh to be sourced already) and returns 1.
# Never exits the parent process — a broken Slack path must never fail a task.
#
# Env:
#   NOTIFY_SOPS_DIR   directory containing secrets/slack.yaml
#                     (default /srv/rzware-ceo)
#   NOTIFY_CHANNEL    fallback channel when CHANNEL arg is omitted
#                     (default #rzware-ceo)

NOTIFY_SOPS_DIR="${NOTIFY_SOPS_DIR:-/srv/rzware-ceo}"
NOTIFY_CHANNEL="${NOTIFY_CHANNEL:-#rzware-ceo}"

# notify_slack CHANNEL TEXT
notify_slack() {
  local channel="${1:-$NOTIFY_CHANNEL}"
  local text="${2:-}"
  local payload_file rc

  if ! command -v jq >/dev/null 2>&1; then
    log_warn "notify_slack: jq not found; skipping" channel="$channel"
    return 1
  fi

  if [[ ! -f "$NOTIFY_SOPS_DIR/secrets/slack.yaml" ]]; then
    log_warn "notify_slack: sops secrets file not found; skipping" \
      path="$NOTIFY_SOPS_DIR/secrets/slack.yaml"
    return 1
  fi

  payload_file="$(mktemp /tmp/emf-notify-XXXXXX.json)"
  jq -nc --arg channel "$channel" --arg text "$text" \
    '{channel: $channel, text: $text}' > "$payload_file"

  (
    cd "$NOTIFY_SOPS_DIR" && \
    sops exec-env secrets/slack.yaml \
      "curl -sS --max-time 10 -X POST \
        -H 'Authorization: Bearer \$SLACK_BOT_TOKEN' \
        -H 'Content-type: application/json; charset=utf-8' \
        --data @${payload_file} \
        https://slack.com/api/chat.postMessage" >/dev/null
  )
  rc=$?
  rm -f "$payload_file"

  if (( rc != 0 )); then
    log_warn "notify_slack: post failed; continuing" channel="$channel" rc="$rc"
    return 1
  fi
  return 0
}
