#!/usr/bin/env bash
# Unit tests for the repo-registry helpers in lib/queue.sh.
#
# Invoked directly (`bash queue-repo-test.sh`). Not part of /verify — the
# dispatcher lives outside the maven/npm build. The Playwright wrapper at
# e2e-tests/tests/dispatcher/repo-registry.spec.ts calls this file so it also
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
assert_nonzero() {
  local msg="$1" rc="$2"
  (( rc != 0 )) && _ok "$msg" || _no "$msg" "expected nonzero exit"
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# --- queue_repo_env_var ------------------------------------------------------
assert_eq "env_var: emf"            "RZWARE_REPO_EMF"           "$(queue_repo_env_var emf)"
assert_eq "env_var: dashes → _"     "RZWARE_REPO_SPOTOPENED_WEB" "$(queue_repo_env_var spotopened-web)"
assert_eq "env_var: mixed case"     "RZWARE_REPO_HOMELAB_ARGO"   "$(queue_repo_env_var Homelab-Argo)"
assert_eq "env_var: underscore ok"  "RZWARE_REPO_RZWARE_WEBSITE" "$(queue_repo_env_var rzware_website)"

# --- queue_resolve_repo: emf default ----------------------------------------
EMF_REPO="$TMP/fake-emf"
mkdir -p "$EMF_REPO/.git"
assert_eq "resolve: empty → EMF_REPO" "$EMF_REPO" "$(queue_resolve_repo '')"
assert_eq "resolve: 'emf' → EMF_REPO" "$EMF_REPO" "$(queue_resolve_repo emf)"

# --- queue_resolve_repo: env var wins ---------------------------------------
mkdir -p "$TMP/spot"
RZWARE_REPO_SPOTOPENED_WEB="$TMP/spot" \
  actual="$(queue_resolve_repo spotopened-web)" && rc=0 || rc=$?
assert_eq "resolve: env var → path" "$TMP/spot" "$actual"

# --- queue_resolve_repo: env var pointing at missing dir → fail --------------
RZWARE_REPO_GHOST="$TMP/does/not/exist" \
  actual="$(queue_resolve_repo ghost 2>/dev/null)"; rc=$?
assert_eq "resolve: missing env path → empty stdout" "" "$actual"
assert_nonzero "resolve: missing env path → nonzero exit" "$rc"

# --- queue_resolve_repo: convention fallback --------------------------------
HOME="$TMP/homefallback"
mkdir -p "$HOME/GitHub/couchpicks/.git"
unset RZWARE_REPO_COUCHPICKS
actual="$(queue_resolve_repo couchpicks)"; rc=$?
assert_eq "resolve: convention fallback path" "$HOME/GitHub/couchpicks" "$actual"

# --- queue_resolve_repo: unknown repo → nonzero + no path -------------------
actual="$(queue_resolve_repo nonexistent-repo 2>/dev/null)"; rc=$?
assert_eq "resolve: unknown → empty stdout" "" "$actual"
assert_nonzero "resolve: unknown → nonzero exit" "$rc"

# --- queue_repo_default_branch: reads origin/HEAD ---------------------------
REPO="$TMP/branchrepo"
git init -q -b master "$REPO"
git -C "$REPO" commit --allow-empty -q -m init
git init -q --bare "$TMP/branchrepo.git"
# Prime the bare so master exists as origin.
(cd "$REPO" && git remote add origin "$TMP/branchrepo.git" && git push -q origin master)
# origin/HEAD needs to be set — mirror what a fresh clone does.
git -C "$REPO" remote set-head origin master >/dev/null 2>&1
actual="$(queue_repo_default_branch "$REPO")"
assert_eq "default_branch: master repo → master" "master" "$actual"

# Repo with no origin at all → fallback 'main'.
NOORIG="$TMP/noorigin"
git init -q "$NOORIG"
actual="$(queue_repo_default_branch "$NOORIG")"
assert_eq "default_branch: no origin → main fallback" "main" "$actual"

printf '\n%d passed, %d failed\n' "$pass" "$fail"
(( fail == 0 ))
