#!/bin/sh
# kelta CLI installer (macOS / Linux).
#   curl -fsSL https://downloads.kelta.io/cli/install.sh | sh
# Optional: KELTA_INSTALL_DIR (default ~/.local/bin), KELTA_DOWNLOADS_URL.
set -eu

BASE="${KELTA_DOWNLOADS_URL:-https://downloads.kelta.io}"
INSTALL_DIR="${KELTA_INSTALL_DIR:-$HOME/.local/bin}"

os=$(uname -s)
case "$os" in
  Darwin) os=darwin ;;
  Linux) os=linux ;;
  *) echo "Unsupported OS: $os (use install.ps1 on Windows)" >&2; exit 1 ;;
esac
arch=$(uname -m)
case "$arch" in
  x86_64|amd64) arch=x64 ;;
  arm64|aarch64) arch=arm64 ;;
  *) echo "Unsupported architecture: $arch" >&2; exit 1 ;;
esac
target="$os-$arch"

version=$(curl -fsSL "$BASE/cli/latest.txt" | tr -d '[:space:]')
[ -n "$version" ] || { echo "Could not read $BASE/cli/latest.txt" >&2; exit 1; }

file="kelta-$target"
url="$BASE/cli/releases/$version/$file"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

echo "Downloading kelta $version ($target)…"
curl -fsSL "$url" -o "$tmp/$file"
curl -fsSL "$BASE/cli/releases/$version/SHA256SUMS" -o "$tmp/SHA256SUMS"

expected=$(grep " $file\$" "$tmp/SHA256SUMS" | cut -d' ' -f1)
[ -n "$expected" ] || { echo "No checksum for $file in SHA256SUMS" >&2; exit 1; }
if command -v sha256sum >/dev/null 2>&1; then
  actual=$(sha256sum "$tmp/$file" | cut -d' ' -f1)
else
  actual=$(shasum -a 256 "$tmp/$file" | cut -d' ' -f1)
fi
[ "$expected" = "$actual" ] || { echo "Checksum mismatch — aborting" >&2; exit 1; }

mkdir -p "$INSTALL_DIR"
mv "$tmp/$file" "$INSTALL_DIR/kelta"
chmod 755 "$INSTALL_DIR/kelta"

echo "kelta $version installed to $INSTALL_DIR/kelta"
case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *) echo "NOTE: $INSTALL_DIR is not on your PATH — add it, e.g.:"
     echo "  export PATH=\"$INSTALL_DIR:\$PATH\"" ;;
esac
echo "Get started: kelta auth login --url https://api.kelta.io --tenant <slug>"
