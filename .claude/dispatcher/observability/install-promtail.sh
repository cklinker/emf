#!/usr/bin/env bash
# Install Promtail on worker-01 (craig@192.168.0.232).
#
# Idempotent — safe to re-run after config changes. Pulls latest Promtail
# binary, installs the config from this dir, drops the systemd unit,
# enables + starts.
#
# Usage:
#   ssh worker-01 'bash ~/GitHub/emf/.claude/dispatcher/observability/install-promtail.sh'
#
# Requires sudo (for /usr/local/bin, /etc/promtail, /var/lib/promtail,
# /etc/systemd/system).

set -euo pipefail

PROMTAIL_VERSION="${PROMTAIL_VERSION:-v3.4.2}"
INSTALL_DIR="/usr/local/bin"
CONFIG_DIR="/etc/promtail"
DATA_DIR="/var/lib/promtail"
SYSTEMD_DIR="/etc/systemd/system"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# No upfront `sudo -v` — that always prompts for a password even when
# specific commands are NOPASSWD-allowed. Each sudo call below is an
# allowed command (install, cp, systemctl).

# 1. Binary
if ! command -v promtail >/dev/null 2>&1 || ! promtail --version 2>&1 | grep -q "${PROMTAIL_VERSION#v}"; then
  echo "[install-promtail] installing Promtail $PROMTAIL_VERSION"
  tmp="$(mktemp -d)"
  curl -fsSL "https://github.com/grafana/loki/releases/download/${PROMTAIL_VERSION}/promtail-linux-amd64.zip" \
    -o "$tmp/promtail.zip"
  unzip -q "$tmp/promtail.zip" -d "$tmp"
  sudo install -m 755 "$tmp/promtail-linux-amd64" "$INSTALL_DIR/promtail"
  rm -rf "$tmp"
else
  echo "[install-promtail] Promtail $PROMTAIL_VERSION already installed"
fi

# 2. Config
sudo install -d -o root -g root "$CONFIG_DIR"
sudo install -d -o root -g root "$DATA_DIR"
sudo install -m 644 -o root -g root \
  "$SCRIPT_DIR/promtail/promtail-config.yml" \
  "$CONFIG_DIR/config.yml"

# 3. systemd unit
sudo install -m 644 -o root -g root \
  "$SCRIPT_DIR/promtail/promtail.service" \
  "$SYSTEMD_DIR/promtail.service"
sudo systemctl daemon-reload

# 4. Enable + start
sudo systemctl enable --now promtail
sleep 2

# 5. Verify
echo
echo "[install-promtail] systemd status:"
systemctl status promtail --no-pager -n 5 || true

echo
echo "[install-promtail] tail of logs:"
sudo journalctl -u promtail --no-pager -n 10 || true

echo
echo "[install-promtail] done. Verify Loki ingestion:"
echo "  curl -s 'https://loki.rzware.com/loki/api/v1/labels' | jq ."
