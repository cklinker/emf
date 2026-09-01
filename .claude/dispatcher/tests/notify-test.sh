#!/usr/bin/env bash
# Unit tests for notify_slack() in lib/notify.sh.
# Tests error-handling paths that don't require real Slack credentials, plus
# a stub-sops happy path that validates the payload JSON shape.
#
# Invoked directly (`bash notify-test.sh`). Not part of /verify — the
# dispatcher lives outside the maven/npm build. The Playwright wrapper at
# e2e-tests/tests/dispatcher/notify.spec.ts calls this file so it also
# shows up in git diffs against origin/main.

set -u
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_LIB="$SELF_DIR/../lib/log.sh"
NOTIFY_LIB="$SELF_DIR/../lib/notify.sh"
[[ -f "$LOG_LIB" ]]    || { echo "missing $LOG_LIB"    >&2; exit 2; }
[[ -f "$NOTIFY_LIB" ]] || { echo "missing $NOTIFY_LIB" >&2; exit 2; }

# shellcheck source=/dev/null
. "$LOG_LIB"
# shellcheck source=/dev/null
. "$NOTIFY_LIB"

pass=0; fail=0
_ok() { pass=$((pass+1)); printf 'ok   %s\n' "$1"; }
_no() { fail=$((fail+1)); printf 'FAIL %s\n     %s\n' "$1" "$2"; }
assert_zero()    { local msg="$1" rc="$2"; (( rc == 0 )) && _ok "$msg" || _no "$msg" "expected 0, got $rc"; }
assert_nonzero() { local msg="$1" rc="$2"; (( rc != 0 )) && _ok "$msg" || _no "$msg" "expected nonzero, got 0"; }
assert_eq()      { local msg="$1" want="$2" got="$3"; [[ "$want" == "$got" ]] && _ok "$msg" || _no "$msg" "want=[$want] got=[$got]"; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Suppress log noise during tests.
EMF_LOG_DIR="$TMP/logs"
mkdir -p "$EMF_LOG_DIR"
log_init "notify-test"

# ---- 1. Missing sops secrets file → returns 1 --------------------------------

NOTIFY_SOPS_DIR="$TMP/nonexistent"
notify_slack "#test" "hello" 2>/dev/null; rc=$?
assert_nonzero "missing sops dir: returns 1" "$rc"

# ---- 2. Sops dir present but no secrets/slack.yaml → returns 1 ---------------

mkdir -p "$TMP/nosecrets"
NOTIFY_SOPS_DIR="$TMP/nosecrets"
notify_slack "#test" "hello" 2>/dev/null; rc=$?
assert_nonzero "sops dir exists but no secrets/slack.yaml: returns 1" "$rc"

# ---- 3. Caller continues after notify failure when using || true -------------

_ran=0
NOTIFY_SOPS_DIR="$TMP/nonexistent"
notify_slack "#test" "will fail" 2>/dev/null || true
_ran=1
assert_eq "|| true: caller reaches next line after failure" "1" "$_ran"

# ---- 4. Caller under set -uo pipefail continues after notify failure ---------

echo "before" > "$TMP/caller-out.txt"
(
  set -uo pipefail
  NOTIFY_SOPS_DIR="$TMP/nonexistent"
  notify_slack "#test" "failing" 2>/dev/null || true
  echo "after" >> "$TMP/caller-out.txt"
) 2>/dev/null
if grep -q "after" "$TMP/caller-out.txt"; then
  _ok "caller with set -uo pipefail continues after notify failure"
else
  _no "caller with set -uo pipefail continues after notify failure" "script did not reach 'after'"
fi

# ---- 5. Stub sops + curl: happy path, valid JSON payload --------------------

# Fake sops secrets directory (separate from the stubs directory on PATH).
mkdir -p "$TMP/fake-ceo/secrets"
touch "$TMP/fake-ceo/secrets/slack.yaml"

CAPTURED="$TMP/captured.json"

# Stubs go in their own directory to avoid name conflicts with the fake-ceo tree.
mkdir -p "$TMP/stubs"

# stub sops: injects SLACK_BOT_TOKEN and runs the last argument in a shell
cat > "$TMP/stubs/sops" <<'EOF'
#!/usr/bin/env bash
export SLACK_BOT_TOKEN="test-token"
eval "${@: -1}"
EOF
chmod +x "$TMP/stubs/sops"

# stub curl: extracts --data @<file>, copies payload, prints a fake ok response.
# The file path is a positional argument; we iterate until we find --data.
cat > "$TMP/stubs/curl" <<EOF
#!/usr/bin/env bash
while [[ \$# -gt 0 ]]; do
  if [[ "\$1" == "--data" ]]; then
    shift
    src="\${1#@}"
    cp "\$src" "${CAPTURED}" 2>/dev/null
    break
  fi
  shift
done
printf '{"ok":true}\n'
EOF
chmod +x "$TMP/stubs/curl"

OLD_PATH="$PATH"
PATH="$TMP/stubs:$OLD_PATH"

NOTIFY_SOPS_DIR="$TMP/fake-ceo"
notify_slack "#rzware-ceo" "test message from notify-test" 2>/dev/null; rc=$?

PATH="$OLD_PATH"

assert_zero "stub sops+curl: returns 0" "$rc"

if [[ -f "$CAPTURED" ]]; then
  channel_val="$(jq -r '.channel' "$CAPTURED" 2>/dev/null)"
  text_val="$(jq -r '.text'    "$CAPTURED" 2>/dev/null)"
  assert_eq "payload .channel" "#rzware-ceo" "$channel_val"
  assert_eq "payload .text"    "test message from notify-test" "$text_val"
else
  _no "payload file captured" "curl stub did not write $CAPTURED"
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
(( fail == 0 ))
