#!/usr/bin/env bash
# Unit tests for cost_ceiling_exceeded() in lib/queue.sh.
#
# Invoked directly (`bash spend-ceiling-test.sh`). Not part of /verify — the
# dispatcher lives outside the maven/npm build. The Playwright wrapper at
# e2e-tests/tests/dispatcher/spend-ceiling.spec.ts calls this file so it also
# shows up in git diffs against origin/main.

set -u
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB="$SELF_DIR/../lib/queue.sh"
[[ -f "$LIB" ]] || { echo "missing $LIB" >&2; exit 2; }

# shellcheck source=/dev/null
. "$LIB"

pass=0; fail=0
_ok() { pass=$((pass+1)); printf 'ok   %s\n' "$1"; }
_no() { fail=$((fail+1)); printf 'FAIL %s\n     %s\n' "$1" "$2"; }
assert_eq() {
  local msg="$1" want="$2" got="$3"
  [[ "$want" == "$got" ]] && _ok "$msg" || _no "$msg" "want=[$want] got=[$got]"
}
assert_zero() {
  local msg="$1" rc="$2"
  (( rc == 0 )) && _ok "$msg" || _no "$msg" "expected exit 0 (exceeded), got $rc"
}
assert_nonzero() {
  local msg="$1" rc="$2"
  (( rc != 0 )) && _ok "$msg" || _no "$msg" "expected nonzero exit (not exceeded), got $rc"
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Point cost_ceiling_exceeded() at our temp dir instead of /var/log/emf-dispatcher.
EMF_LOG_DIR="$TMP"

# Timestamps: current UTC time maps to today in America/Denver.
# 24-hours-ago UTC maps to yesterday-or-earlier in Denver (outside today's window).
TODAY_TS="$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"
YESTERDAY_TS="$(date -u -d '24 hours ago' +%Y-%m-%dT%H:%M:%S.000Z)"

write_cost() {
  local file="$TMP/cost-$1.json" ts="$2" cost="$3"
  printf '{"ts":"%s","task":"%s","final_state":"MERGED","duration_sec":100,"input_tokens":0,"cache_creation_tokens":0,"cache_read_tokens":0,"output_tokens":0,"request_count":1,"estimated_cost_usd":%s,"component":"cost"}\n' \
    "$ts" "$1" "$cost" > "$file"
}

# --- DAILY_COST_CEILING_USD unset → never exceeded ---------------------------

unset DAILY_COST_CEILING_USD
write_cost "task-unset-a" "$TODAY_TS" "999.99"
cost_ceiling_exceeded; rc=$?
assert_nonzero "unset ceiling: not exceeded regardless of spend" "$rc"
rm -f "$TMP"/cost-*.json

# --- sum under ceiling → not exceeded ----------------------------------------

DAILY_COST_CEILING_USD="100.00"
write_cost "task-under-a" "$TODAY_TS" "30.00"
write_cost "task-under-b" "$TODAY_TS" "25.50"
# total = 55.50, ceiling = 100 → not exceeded
cost_ceiling_exceeded; rc=$?
assert_nonzero "under ceiling (55.50 < 100.00): not exceeded" "$rc"
rm -f "$TMP"/cost-*.json

# --- sum exactly at ceiling → exceeded ----------------------------------------

DAILY_COST_CEILING_USD="50.00"
write_cost "task-at-a" "$TODAY_TS" "30.00"
write_cost "task-at-b" "$TODAY_TS" "20.00"
# total = 50.00, ceiling = 50 → exceeded (at == ceiling)
cost_ceiling_exceeded; rc=$?
assert_zero "at ceiling (50.00 >= 50.00): exceeded" "$rc"
rm -f "$TMP"/cost-*.json

# --- sum over ceiling → exceeded ---------------------------------------------

DAILY_COST_CEILING_USD="80.00"
write_cost "task-over-a" "$TODAY_TS" "50.00"
write_cost "task-over-b" "$TODAY_TS" "40.00"
# total = 90.00, ceiling = 80 → exceeded
cost_ceiling_exceeded; rc=$?
assert_zero "over ceiling (90.00 >= 80.00): exceeded" "$rc"
rm -f "$TMP"/cost-*.json

# --- yesterday files excluded from today's sum --------------------------------

DAILY_COST_CEILING_USD="10.00"
write_cost "task-yesterday" "$YESTERDAY_TS" "999.00"   # huge but yesterday → excluded
write_cost "task-today"     "$TODAY_TS"     "5.00"     # today → included; total = 5 < 10
cost_ceiling_exceeded; rc=$?
assert_nonzero "yesterday files excluded (5.00 today < 10.00 ceiling): not exceeded" "$rc"
rm -f "$TMP"/cost-*.json

# --- no cost files at all → not exceeded -------------------------------------

DAILY_COST_CEILING_USD="1.00"
# (no files written)
cost_ceiling_exceeded; rc=$?
assert_nonzero "no cost files: not exceeded" "$rc"

printf '\n%d passed, %d failed\n' "$pass" "$fail"
(( fail == 0 ))
