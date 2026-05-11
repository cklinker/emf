#!/usr/bin/env bash
# Install (or refresh) the Mac launchd agents for the EMF autopilot.
#
# Idempotent — safe to re-run any time the plists change. Copies the plists
# from this dir to ~/Library/LaunchAgents/, then bootout + bootstrap each one
# so launchd picks up the new copy (a plain `cp` is not enough — the running
# launchd keeps the old definition until it is reloaded).
#
# Usage:
#   bash ~/GitHub/emf/.claude/dispatcher/launchd/install-launchd.sh
#
# No sudo required — gui/$(id -u) is the per-user domain.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$HOME/Library/LaunchAgents"
UID_NUM="$(id -u)"
DOMAIN="gui/$UID_NUM"

AGENTS=(
  "com.cklinker.emf-planner"
  "com.cklinker.emf-status"
)

install -d "$TARGET_DIR"

for label in "${AGENTS[@]}"; do
  src="$SCRIPT_DIR/${label}.plist"
  dst="$TARGET_DIR/${label}.plist"

  if [[ ! -f "$src" ]]; then
    echo "[install-launchd] missing source plist: $src" >&2
    exit 1
  fi

  echo "[install-launchd] copying $label.plist"
  install -m 644 "$src" "$dst"

  echo "[install-launchd] reloading $label"
  launchctl bootout "$DOMAIN" "$dst" 2>/dev/null || true
  launchctl bootstrap "$DOMAIN" "$dst"
done

echo
echo "[install-launchd] launchctl list (emf agents):"
launchctl list | grep emf || true

# Verify every agent is registered. `launchctl list` prints PID + status +
# label, one per line. Missing = bootstrap silently failed.
missing=0
for label in "${AGENTS[@]}"; do
  if ! launchctl list | awk '{print $3}' | grep -qx "$label"; then
    echo "[install-launchd] ERROR: $label not registered" >&2
    missing=1
  fi
done

if [[ $missing -ne 0 ]]; then
  exit 1
fi

echo
echo "[install-launchd] done. Tail logs:"
echo "  tail -f /tmp/emf-planner.launchd.log"
echo "  tail -f /tmp/emf-status.launchd.log"
echo "  cat /tmp/emf-status.txt"
